//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2026 Qore Technologies, s.r.o.
//
//  Permission is hereby granted, free of charge, to any person obtaining a
//  copy of this software and associated documentation files (the "Software"),
//  to deal in the Software without restriction, including without limitation
//  the rights to use, copy, modify, merge, publish, distribute, sublicense,
//  and/or sell copies of the Software, and to permit persons to whom the
//  Software is furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
//  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
//  DEALINGS IN THE SOFTWARE.
//
//------------------------------------------------------------------------------
#include "defs.h"

#include <algorithm>
#include <atomic>
#include <utility>
#include <vector>
#include <qore/Qore.h>
#include "Jvm.h"
#include "LocalReference.h"
#include "Globals.h"
#include "QoreJniClassMap.h"
#include "JavaToQore.h"

namespace jni {

static const char* this_file = q_basenameptr(__FILE__);

thread_local const JavaExceptionTextMasker* JavaExceptionMaskHelper::current = nullptr;

class JniCallStack : public QoreCallStack {
public:
    DLLLOCAL JniCallStack(jobject throwable, QoreExternalProgramLocationWrapper& loc) {
        Env env;

        try {
            LocalReference<jobjectArray> jstack = env.callObjectMethod(throwable, Globals::methodThrowableGetStackTrace, nullptr).as<jobjectArray>();
            if (jstack) {
                QoreString code;
                for (jsize i = 0, e = env.getArrayLength(jstack); i < e; ++i) {
                    LocalReference<jobject> jste = env.getObjectArrayElement(jstack, i);

                    LocalReference<jstring> jcls = env.callObjectMethod(jste, Globals::methodStackTraceElementGetClassName, nullptr).as<jstring>();
                    jni::Env::GetStringUtfChars cname(env, jcls);
                    LocalReference<jstring> jmethod = env.callObjectMethod(jste, Globals::methodStackTraceElementGetMethodName, nullptr).as<jstring>();
                    jni::Env::GetStringUtfChars mname(env, jmethod);
                    LocalReference<jstring> jfilename = env.callObjectMethod(jste, Globals::methodStackTraceElementGetFileName, nullptr).as<jstring>();
                    jni::Env::GetStringUtfChars file(env, jfilename);
                    jint line = env.callIntMethod(jste, Globals::methodStackTraceElementGetLineNumber, nullptr);
                    // Java uses negative line numbers for unavailable source locations (-2 for native
                    // methods). Qore represents all unknown locations with -1, including exception frames.
                    if (line < -1) {
                        line = -1;
                    }
                    jboolean native = env.callBooleanMethod(jste, Globals::methodStackTraceElementIsNativeMethod, nullptr);

                    printd(LogLevel, "JniCallStack::JniCallStack() adding %s\n", code.c_str());
                    if (!i) {
                        loc.set(file.c_str(), line, line, nullptr, 0, "Java");
                    } else {
                        add(native ? CT_BUILTIN : CT_USER, file.c_str(), line, line, code.c_str(), "Java");
                    }

                    code.clear();
                    code.sprintf("%s.%s", cname.c_str(), mname.c_str());
                }
                if (!code.empty()) {
                    add(CT_BUILTIN, this_file, __LINE__, __LINE__, code.c_str(), "c++");
                }
            }
        } catch (jni::Exception& e) {
            e.ignore();
        }
    }
};

void JavaException::ignore() {
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = Jvm::getEnv();
    jthrowable raw_throwable = env->ExceptionOccurred();
    assert(raw_throwable != nullptr);
    env->ExceptionClear();
    LocalReference<jthrowable> throwable(raw_throwable);
}

jthrowable JavaException::save() {
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = Jvm::getEnv();
    jthrowable raw_throwable = env->ExceptionOccurred();
    assert(raw_throwable != nullptr);
    env->ExceptionClear();
    LocalReference<jthrowable> throwable(raw_throwable);
    return throwable.release();
}

void JavaException::restore(jthrowable je) {
    // not using the Env wrapper because we don't want any C++ exceptions here
    JNIEnv* env = Jvm::getEnv();
    env->Throw(je);
}

QoreStringNode* JavaException::toString(bool clear) const {
    JNIEnv* env = Jvm::getEnv();         //not using the Env wrapper because we don't want any C++ exceptions here
    LocalReference<jthrowable> throwable = env->ExceptionOccurred();
    assert(throwable != nullptr);

    // Check if Globals is fully initialized - if not, we can't use the normal exception handling
    // This can happen if an exception is thrown during Globals::init() before methodClassGetName is set
    if (!Globals::methodClassGetName) {
        // Print the exception stack trace to stderr for debugging before clearing
        fprintf(stderr, "JNI module init exception (Globals not fully initialized):\n");
        fflush(stderr);
        env->ExceptionDescribe();
        fflush(stderr);
        env->ExceptionClear();
        return new QoreStringNode("Java exception occurred during JNI module initialization (before Globals fully initialized)");
    }

    // no JNI call may be made with a Java exception pending: the exception is cleared while it is described, and
    // raised again afterwards unless it is to be cleared
    env->ExceptionClear();
    QoreStringNode* rv = describe(env, throwable);
    // the secrets of the operation are masked in the text (qore#7344)
    JavaExceptionMaskHelper::mask(*rv);
    if (!clear) {
        env->Throw(throwable);
    }
    return rv;
}

QoreStringNode* JavaException::describe(JNIEnv* env, jthrowable original) {
    LocalReference<jthrowable> throwable(static_cast<jthrowable>(env->NewLocalRef(original)));
    SimpleRefHolder<QoreStringNode> desc(new QoreStringNode(QCS_UTF8));

    while (true) {
        // a QoreExceptionWrapper is described with its message, the code and the description of its Qore
        // exception, which it keeps: Qore takes the exception only when the wrapper is converted (qore#6864)
        if (env->IsInstanceOf(throwable, Globals::classQoreExceptionWrapper)) {
            LocalReference<jstring> msg = static_cast<jstring>(env->CallObjectMethod(throwable,
                Globals::methodThrowableGetMessage));
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            } else if (msg != nullptr) {
                if (!desc->empty()) {
                    desc->concat(": ");
                }
                Env::JniUtfChars chars(env, msg);
                if (!chars.get()) {
                    env->ExceptionClear();
                } else {
                    Env::ModifiedUtf8 m(chars.get());
                    desc->concat(m.c_str(), m.size());
                }
            }
            break;
        }

        LocalReference<jstring> excName = static_cast<jstring>(env->CallObjectMethod(env->GetObjectClass(throwable),
            Globals::methodClassGetName));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return new QoreStringNode("Unable to get exception class name: another exception thrown");
        }

        {
            Env::JniUtfChars chars(env, excName);
            if (!chars.get()) {
                env->ExceptionClear();
                return new QoreStringNode("Unable to get exception class name: GetStringUTFChars() failed");
            }
            if (!desc->empty()) {
                desc->concat(": ");
            }
            Env::ModifiedUtf8 name(chars.get());
            desc->concat(name.c_str(), name.size());
        }

        LocalReference<jstring> msg = static_cast<jstring>(env->CallObjectMethod(throwable, Globals::methodThrowableGetMessage));
        //printd(5, "msg: %p\n", (jstring)msg);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (msg != nullptr) {
            desc->concat(": ");
            Env::JniUtfChars chars(env, msg);
            if (!chars.get()) {
                env->ExceptionClear();
            } else {
                Env::ModifiedUtf8 m(chars.get());
                desc->concat(m.c_str(), m.size());
            }
        }

        /*
        jmethodID methodThrowablePrintStackTrace = env->GetMethodID(Globals::classThrowable, "printStackTrace", "()V");
        env->CallVoidMethod(throwable, methodThrowablePrintStackTrace);
        */

        LocalReference<jthrowable> cause = static_cast<jthrowable>(env->CallObjectMethod(throwable,
            Globals::methodThrowableGetCause));
        //printd(5, "cause: %p\n", (jthrowable)cause);
        if (!cause) {
            break;
        }
        throwable = cause.release();
    }
    return desc.release();
}

//! Returns true if the message of a Java exception names a secret of the masker of the thread (qore#7344)
/** @param env the JNI environment; no Java exception may be pending
*/
static bool message_names_secret(JNIEnv* env, jthrowable throwable) {
    jstring raw_msg = static_cast<jstring>(env->CallObjectMethod(throwable, Globals::methodThrowableGetMessage));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_msg) {
            env->DeleteLocalRef(raw_msg);
        }
        return false;
    }
    if (!raw_msg) {
        return false;
    }
    LocalReference<jstring> msg(raw_msg);
    Env::JniUtfChars chars(env, msg);
    if (!chars.get()) {
        env->ExceptionClear();
        return false;
    }
    Env::ModifiedUtf8 m(chars.get());
    QoreString text(m.c_str(), m.size(), QCS_UTF8);
    return JavaExceptionMaskHelper::mask(text);
}

//! Returns true if a Java exception, its causes, the next exceptions of an SQLException or the exceptions that one of
//! them suppressed name a secret of the masker of the thread in their messages (qore#7344)
/** Each exception is checked once (a cycle ends there); exceptions after 64 of them or 16 levels deep are not
    checked, and true is returned if any were left, as for an error, so that the caller does not keep the exception

    @param env the JNI environment; no Java exception may be pending
*/
static bool throwable_names_secret(JNIEnv* env, jthrowable original) {
    static constexpr size_t max_count = 64;
    static constexpr int max_depth = 16;
    // the local references made here are freed together
    if (env->PushLocalFrame(max_count * 4) < 0) {
        env->ExceptionClear();
        return true;
    }
    bool rv = false;
    jmethodID get_next = env->GetMethodID(Globals::classSQLException, "getNextException",
        "()Ljava/sql/SQLException;");
    jmethodID get_suppressed = get_next ? env->GetMethodID(Globals::classThrowable, "getSuppressed",
        "()[Ljava/lang/Throwable;") : nullptr;
    if (!get_suppressed) {
        env->ExceptionClear();
        rv = true;
    } else {
        std::vector<std::pair<jthrowable, int>> todo = {{original, 0}};
        std::vector<jthrowable> seen;
        // adds an exception to check, if there is one
        auto add = [&todo](jobject t, int depth) {
            if (t) {
                todo.emplace_back(static_cast<jthrowable>(t), depth);
            }
        };
        while (!todo.empty()) {
            if (seen.size() == max_count) {
                rv = true;
                break;
            }
            jthrowable t = todo.back().first;
            int depth = todo.back().second;
            todo.pop_back();
            if (std::any_of(seen.begin(), seen.end(), [env, t](jthrowable s) { return env->IsSameObject(s, t); })) {
                continue;
            }
            seen.push_back(t);
            if (message_names_secret(env, t)) {
                rv = true;
                break;
            }
            if (depth == max_depth) {
                rv = true;
                break;
            }
            jobject cause = env->CallObjectMethod(t, Globals::methodThrowableGetCause);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                rv = true;
                break;
            }
            add(cause, depth + 1);
            if (env->IsInstanceOf(t, Globals::classSQLException)) {
                jobject next = env->CallObjectMethod(t, get_next);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    rv = true;
                    break;
                }
                add(next, depth + 1);
            }
            jobjectArray suppressed = static_cast<jobjectArray>(env->CallObjectMethod(t, get_suppressed));
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                rv = true;
                break;
            }
            if (suppressed) {
                for (jsize i = 0, e = env->GetArrayLength(suppressed); i < e && i < static_cast<jsize>(max_count);
                        ++i) {
                    add(env->GetObjectArrayElement(suppressed, i), depth + 1);
                }
            }
        }
    }
    env->PopLocalFrame(nullptr);
    return rv;
}

//! Returns a java.sql.SQLException with \a msg and the SQLState and vendor code of \a throwable (qore#7344)
/** @param env the JNI environment; no Java exception may be pending
    @param throwable the exception
    @param msg the masked message of \a throwable

    @return the copy, or a null reference if \a throwable is not an SQLException or the copy cannot be made
*/
static LocalReference<jthrowable> masked_sql_exception(JNIEnv* env, jthrowable throwable, const QoreString& msg) {
    if (!env->IsInstanceOf(throwable, Globals::classSQLException)) {
        return nullptr;
    }
    try {
        Env jenv(env);
        jmethodID get_error_code = jenv.getMethod(Globals::classSQLException, "getErrorCode", "()I");
        jmethodID ctor = jenv.getMethod(Globals::classSQLException, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;I)V");
        LocalReference<jstring> state = jenv.callObjectMethod(throwable, Globals::methodSQLExceptionGetSQLState,
            nullptr).as<jstring>();
        jint code = jenv.callIntMethod(throwable, get_error_code, nullptr);
        LocalReference<jstring> jmsg = jenv.newString(msg);
        std::vector<jvalue> jargs(3);
        jargs[0].l = jmsg;
        jargs[1].l = state;
        jargs[2].i = code;
        return jenv.newObject(Globals::classSQLException, ctor, &jargs[0]).as<jthrowable>();
    } catch (jni::Exception& e) {
        e.ignore();
    }
    return nullptr;
}

void JavaException::convert(ExceptionSink* xsink) {
    // Converting the original exception must not throw a second cancellation exception while
    // constructing its Java call stack or argument. The request remains pending afterwards.
    QoreCancelDeferralHelper cancel_deferral;
    JNIEnv* env = Jvm::getEnv();         //not using the Env wrapper because we don't want any C++ exceptions here
    jthrowable raw_throwable = env->ExceptionOccurred();
    assert(raw_throwable != nullptr);
    env->ExceptionClear();
    LocalReference<jthrowable> throwable(raw_throwable);

    if (env->IsInstanceOf(throwable, Globals::classQoreExceptionWrapper)) {
        jlong l = env->CallLongMethod(throwable, Globals::methodQoreExceptionWrapperGet);
        if (env->ExceptionCheck()) {
            // Preserve the original exception if extracting the wrapped sink fails.
            env->ExceptionClear();
        } else if (l != 0) {
            ExceptionSink* src = reinterpret_cast<ExceptionSink *>(l);
            xsink->assimilate(src);
            return;
        }
        //if l is zero, it means that the xsink wrapped in QoreExceptionWrapper has already been consumed. This should
        //not happen, but if it does, we simply report the QoreExceptionWrapper as if it was a normal Java exception
    }

    if (env->IsInstanceOf(throwable, Globals::classQoreException)) {
        try {
            Env jenv(env);
            LocalReference<jstring> err = jenv.callObjectMethod(throwable,
                Globals::methodQoreExceptionGetErr, nullptr).as<jstring>();
            LocalReference<jstring> desc = jenv.callObjectMethod(throwable,
                Globals::methodQoreExceptionGetDesc, nullptr).as<jstring>();
            LocalReference<jobject> arg = jenv.callObjectMethod(throwable,
                Globals::methodQoreExceptionGetArg, nullptr);

            if (err) {
                Env::GetStringUtfChars err_str(jenv, err);
                Env::GetStringUtfChars desc_str(jenv, desc);
                ValueHolder qore_arg(JavaToQore::convertToQore(std::move(arg),
                    jni_get_program_context(), false), xsink);

                QoreExternalProgramLocationWrapper loc;
                JniCallStack callstack(throwable, loc);
                // the description keeps its NUL characters
                SimpleRefHolder<QoreStringNode> description(new QoreStringNode(desc_str.c_str(), desc_str.size(),
                    QCS_UTF8));
                // the secrets of the operation are masked in the text (qore#7344)
                JavaExceptionMaskHelper::mask(**description);
                // the code is passed in the default encoding, which the API assumes for it
                // (a code that cannot be converted is passed as it is)
                QoreString err_utf8(err_str.c_str(), err_str.size(), QCS_UTF8);
                ExceptionSink conv_xsink;
                TempEncodingHelper err_code(err_utf8, QCS_DEFAULT, &conv_xsink);
                const char* code = err_str.c_str();
                if (conv_xsink) {
                    conv_xsink.clear();
                } else {
                    code = err_code->c_str();
                }

                xsink->raiseExceptionArg(loc.get(), code, qore_arg.release(),
                    description.release(), callstack);
                return;
            }
        } catch (jni::Exception& e) {
            // A custom accessor or argument conversion can throw. Report the original
            // Java exception below instead of recursing into the failing conversion.
            e.ignore();
        }
    }

    jclass raw_throwable_class = env->GetObjectClass(throwable);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_throwable_class) {
            env->DeleteLocalRef(raw_throwable_class);
        }
        xsink->raiseException("JNI-ERROR", "Unable to get exception class - another exception thrown");
        return;
    }
    LocalReference<jclass> tcls(raw_throwable_class);

    // Check for Java exceptions before LocalReference validates the result with another JNI call.
    jstring raw_exc_name = static_cast<jstring>(env->CallObjectMethod(tcls,
        Globals::methodClassGetName));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_exc_name) {
            env->DeleteLocalRef(raw_exc_name);
        }
        xsink->raiseException("JNI-ERROR", "Unable to get exception class name - another exception thrown");
        return;
    }
    LocalReference<jstring> excName(raw_exc_name);

    SimpleRefHolder<QoreStringNode> desc;
    {
        Env::JniUtfChars chars(env, excName);
        if (!chars.get()) {
            env->ExceptionClear();
            xsink->raiseException("JNI-ERROR", "Unable to get exception class name - GetStringUTFChars() failed");
            return;
        }
        Env::ModifiedUtf8 name(chars.get());
        desc = new QoreStringNode(name.c_str(), name.size(), QCS_UTF8);
    }

    // the message of the exception
    QoreString msg_text(QCS_UTF8);
    jstring raw_msg = static_cast<jstring>(env->CallObjectMethod(throwable,
        Globals::methodThrowableGetMessage));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_msg) {
            env->DeleteLocalRef(raw_msg);
        }
    } else if (raw_msg != nullptr) {
        LocalReference<jstring> msg(raw_msg);
        desc->concat(": ");
        Env::JniUtfChars chars(env, msg);
        if (!chars.get()) {
            env->ExceptionClear();
        } else {
            Env::ModifiedUtf8 m(chars.get());
            desc->concat(m.c_str(), m.size());
            msg_text.concat(m.c_str(), m.size());
        }
    }

    // add Java call stack to Qore call stack; the stack of the exception that was raised
    QoreExternalProgramLocationWrapper loc;
    JniCallStack stack(throwable, loc);

    // the secrets of the operation are masked in the text (qore#7344)
    bool masked = JavaExceptionMaskHelper::mask(**desc);
    // the Java exception is the argument of the Qore exception, whose text (ex: its toString(), which
    // AbstractConnection::ping() gives), causes, next exceptions and suppressed exceptions can name the secrets too:
    // it is replaced with a copy without them
    bool replace_arg = masked || (JavaExceptionMaskHelper::active() && throwable_names_secret(env, throwable));
    // true if the exception is raised without the Java exception: its text names the secrets, and it is not an
    // SQLException
    bool drop_arg = false;
    if (replace_arg) {
        JavaExceptionMaskHelper::mask(msg_text);
        LocalReference<jthrowable> copy = masked_sql_exception(env, throwable, msg_text);
        if (copy) {
            throwable = std::move(copy);
            tcls = static_cast<jclass>(env->NewLocalRef(Globals::classSQLException));
        } else {
            drop_arg = true;
        }
    }

    // during the module's shutdown, no global context Program exists, and the exception is then raised without the
    // Java exception object
    QoreProgram* pgm = nullptr;
    // holds the global context Program, if the exception is raised in it (qore#6829)
    JniContextProgramHolder ctx_holder;
    jni_get_context_or_global(pgm, ctx_holder);

    if (!pgm || drop_arg) {
        xsink->raiseExceptionArg(loc.get(), "JNI-ERROR", QoreValue(), desc.release(), stack);
    } else {
        Env jenv(env);
        QoreClass* qc;
        try {
            qc = qjcm.findCreateQoreClass(jenv, tcls, pgm);
            xsink->raiseExceptionArg(loc.get(), "JNI-ERROR", new QoreObject(qc, pgm,
                new QoreJniPrivateData(throwable.as<jobject>())), desc.release(), stack);
        } catch (jni::Exception& e) {
            xsink->raiseExceptionArg(loc.get(), "JNI-ERROR", QoreValue(), desc.release());
        }
    }
}

void JavaException::ignoreOrRethrowNoClass() {
    JNIEnv* env = Jvm::getEnv();         //not using the Env wrapper because we don't want any C++ exceptions here
    jthrowable raw_throwable = env->ExceptionOccurred();
    assert(raw_throwable != nullptr);
    env->ExceptionClear();
    LocalReference<jthrowable> throwable(raw_throwable);

    // to show an unhandled exception on the console
    ExceptionSink xsink;

    if (env->IsInstanceOf(throwable, Globals::classQoreExceptionWrapper)) {
        jlong l = env->CallLongMethod(throwable, Globals::methodQoreExceptionWrapperGet);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        } else if (l != 0) {
            ExceptionSink *src = reinterpret_cast<ExceptionSink *>(l);
            xsink.assimilate(src);
            return;
        }
        //if l is zero, it means that the xsink wrapped in QoreExceptionWrapper has already been consumed. This should
        //not happen, but if it does, we simply report the QoreExceptionWrapper as if it was normal Java exception
    }

    jclass raw_throwable_class = env->GetObjectClass(throwable);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_throwable_class) {
            env->DeleteLocalRef(raw_throwable_class);
        }
        xsink.raiseException("JNI-ERROR", "Unable to get exception class: another exception thrown");
        return;
    }
    LocalReference<jclass> throwable_class(raw_throwable_class);

    jstring raw_exc_name = static_cast<jstring>(env->CallObjectMethod(throwable_class,
        Globals::methodClassGetName));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_exc_name) {
            env->DeleteLocalRef(raw_exc_name);
        }
        xsink.raiseException("JNI-ERROR", "Unable to get exception class name: another exception thrown");
        return;
    }
    LocalReference<jstring> excName(raw_exc_name);

    SimpleRefHolder<QoreStringNode> desc;
    {
        Env::JniUtfChars chars(env, excName);
        if (!chars.get()) {
            env->ExceptionClear();
            xsink.raiseException("JNI-ERROR", "Unable to get exception class name: GetStringUTFChars() failed");
            return;
        }
        // return if this is the exception we should ignore (a class name is compared: ASCII)
        if (!strcmp(chars.get(), "java.lang.ClassNotFoundException")
            || !strcmp(chars.get(), "java.lang.NoClassDefFoundError")) {
            return;
        }
        Env::ModifiedUtf8 name(chars.get());
        desc = new QoreStringNode(name.c_str(), name.size(), QCS_UTF8);
    }

    jstring raw_msg = static_cast<jstring>(env->CallObjectMethod(throwable, Globals::methodThrowableGetMessage));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (raw_msg) {
            env->DeleteLocalRef(raw_msg);
        }
    } else if (raw_msg != nullptr) {
        LocalReference<jstring> msg(raw_msg);
        desc->concat(": ");
        Env::JniUtfChars chars(env, msg);
        if (!chars.get()) {
            env->ExceptionClear();
        } else {
            Env::ModifiedUtf8 m(chars.get());
            desc->concat(m.c_str(), m.size());
        }
    }

    // add Java call stack to Qore call stack
    QoreExternalProgramLocationWrapper loc;
    JniCallStack stack(throwable, loc);

    xsink.raiseExceptionArg(loc.get(), "JNI-ERROR", new QoreObject(QC_THROWABLE, getProgram(), new QoreJniPrivateData(throwable.as<jobject>())), desc.release(), stack);
}

// workaround for https://bugs.openjdk.java.net/browse/JDK-8221530
bool JavaException::checkBug_8221530() {
    JNIEnv* env = Jvm::getEnv();         //not using the Env wrapper because we don't want any C++ exceptions here
    LocalReference<jthrowable> throwable = save();
    assert(throwable);

    LocalReference<jstring> excName = static_cast<jstring>(env->CallObjectMethod(env->GetObjectClass(throwable), Globals::methodClassGetName));
    if (env->ExceptionCheck()) {
        // Unable to get exception class name: another exception thrown
        env->ExceptionClear();
        restore(throwable.release());
        return true;
    }

    {
        // the characters are released when this block ends; this function never called ReleaseStringUTFChars()
        Env::JniUtfChars chars(env, excName);
        if (!chars.get()) {
            // Unable to get exception class name: GetStringUTFChars() failed
            env->ExceptionClear();
            restore(throwable.release());
            return true;
        }

        // return if this is the exception we should ignore (a class name is compared: ASCII)
        if (strcmp(chars.get(), "java.lang.NullPointerException")) {
            restore(throwable.release());
            return true;
        }
    }

    // check if the exception happened in AccessibleObject
    QoreExternalProgramLocationWrapper loc;
    JniCallStack callstack(throwable, loc);

    // check that the last element in the callstack (i.e. the first call) is a call to
    // "java.lang.reflect.*.setAccessible()"
    if (callstack.empty()) {
        restore(throwable.release());
        return true;
    }

    const std::string& code = callstack.back().code;
    size_t i = code.find("java.lang.reflect");
    if (i != 0) {
        restore(throwable.release());
        return true;
    }
    i = code.find("setAccessible");
    if (i != code.size() - 13) {
        restore(throwable.release());
        return true;
    }

    return false;
}

void jni_append_utf8(QoreString& utf8, QoreValue v) {
    assert(utf8.getEncoding() == QCS_UTF8);
    ExceptionSink xsink;
    QoreStringValueHelper str(v, QCS_UTF8, &xsink);
    if (xsink) {
        // the string cannot be converted to UTF-8: its bytes are appended as they are
        xsink.clear();
        QoreStringValueHelper raw(v);
        utf8.concat(raw->c_str(), raw->size());
        return;
    }
    utf8.concat(str->c_str(), str->size());
}

QoreString jni_utf8_to_default(const char* utf8) {
    QoreString src(utf8, QCS_UTF8);
    ExceptionSink xsink;
    TempEncodingHelper str(src, QCS_DEFAULT, &xsink);
    if (xsink) {
        // the string cannot be converted: it is returned as it is
        xsink.clear();
        return src;
    }
    return QoreString(*str);
}

// set by the module's atexit() handler, where a jni::Exception is not converted to describe it (see
// jni_report_set_plain())
static std::atomic<bool> report_plain{false};

void jni_report_set_plain() noexcept {
    report_plain.store(true, std::memory_order_release);
}

void jni_report_current_exception(const char* step) noexcept {
    try {
        throw;
    } catch (Exception& e) {
        if (report_plain.load(std::memory_order_acquire)) {
            fprintf(stderr, "jni module: %s failed with a jni exception\n", step);
            // a Java exception must not stay pending for the JNI calls that follow
            try {
                e.ignore();
            } catch (...) {
            }
            return;
        }
        try {
            // there is no exception sink in the teardown: the exception is converted to describe it
            ExceptionSink xsink;
            e.convert(&xsink);
            QoreStringValueHelper err(xsink.getExceptionErr());
            QoreStringValueHelper desc(xsink.getExceptionDesc());
            fprintf(stderr, "jni module: %s failed: %s: %s\n", step, err->c_str(), desc->c_str());
            xsink.clear();
        } catch (...) {
            fprintf(stderr, "jni module: %s failed with a jni exception that cannot be described\n", step);
        }
    } catch (std::exception& e) {
        fprintf(stderr, "jni module: %s failed: %s\n", step, e.what());
    } catch (...) {
        fprintf(stderr, "jni module: %s failed with an unknown C++ exception\n", step);
    }
}
} // namespace jni

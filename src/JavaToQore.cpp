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

#include <qore/Qore.h>

#include "QoreJniClassMap.h"
#include "Globals.h"
#include "JavaToQore.h"
#include "QoreJniFunctionalInterface.h"

namespace jni {

namespace {
//! Ends the use of the native handle of a Qore object or closure that its qoreJniEnterHandle() began (qore#6864)
/** The exit method is called when the helper is destroyed, also while a C++ exception unwinds with a Java exception
    pending, which is kept.
*/
class JniHandleUseHelper {
public:
    DLLLOCAL JniHandleUseHelper(Env& env, jobject obj, jmethodID exit) : jenv(*env), obj(obj), exit(exit) {
    }

    DLLLOCAL ~JniHandleUseHelper() {
        // no JNI call may be made with a Java exception pending
        jthrowable pending = jenv->ExceptionOccurred();
        if (pending) {
            jenv->ExceptionClear();
        }
        jenv->CallVoidMethod(obj, exit);
        if (jenv->ExceptionCheck()) {
            // the exit method raises no exception but an error of the JVM (ex: an OutOfMemoryError), which is
            // dropped: the destructor cannot raise it
            jenv->ExceptionClear();
        }
        if (pending) {
            jenv->Throw(pending);
            jenv->DeleteLocalRef(pending);
        }
    }

private:
    JNIEnv* jenv;
    jobject obj;
    jmethodID exit;

    JniHandleUseHelper(const JniHandleUseHelper&) = delete;
    JniHandleUseHelper& operator=(const JniHandleUseHelper&) = delete;
};
} // namespace

//! Discards the values converted for a Java Map or List that the conversion still holds when it is left with an exception
/** A QoreValue does not own its value, so the values converted so far would leak if the conversion of a nested value
    (ex: a Map with a key that has a NUL character, qore#6738) or a Java call threw an exception.  The explicit error
    paths of the conversion discard the values themselves and clear the vector.
*/
template <typename Vec, typename GetValue>
class JniConvertedValuesGuard {
public:
    DLLLOCAL JniConvertedValuesGuard(Vec& values, GetValue get) : values(values), get(get) {
    }

    DLLLOCAL ~JniConvertedValuesGuard() {
        if (values.empty()) {
            return;
        }
        // a Java exception that is unwinding (JavaException) stays pending until it is converted: it is set aside
        // while the values are discarded, which can run Java code (ex: the destructor of a Qore object)
        JNIEnv* jenv = Jvm::getEnv();
        jthrowable pending = jenv->ExceptionOccurred();
        if (pending) {
            jenv->ExceptionClear();
        }
        ExceptionSink xsink;
        for (auto& i : values) {
            get(i).discard(&xsink);
        }
        // an exception raised while discarding a value as another exception is unwinding is dropped
        xsink.clear();
        if (pending) {
            jenv->ExceptionClear();
            jenv->Throw(pending);
            jenv->DeleteLocalRef(pending);
        }
    }

private:
    Vec& values;
    GetValue get;
};

QoreValue JavaToQore::convertToQore(LocalReference<jobject> v, QoreProgram* pgm, bool compat_types,
        NumericOption numeric) {
    if (!v) {
        return QoreValue();
    }

    Env env;

    // convert to Qore value if possible
    if (env.isInstanceOf(v, Globals::classString)) {
        Env::GetStringUtfChars chars(env, v.as<jstring>());
        // the length is given: the string may have NUL characters
        return QoreValue(new QoreStringNode(chars.c_str(), chars.size(), QCS_UTF8));
    }

    if (env.isInstanceOf(v, Globals::classZonedDateTime)) {
        // ISO_OFFSET_DATE_TIME always writes the seconds; toString() leaves them out when they are zero
        // ("2025-01-15T00:00-05:00[America/New_York]"), and the offset after HH:mm was then not parsed, so a
        // date at a whole minute lost its time zone
        jvalue arg;
        arg.l = Globals::isoOffsetDateTimeFormatter;
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodZonedDateTimeFormat, &arg).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    // issue #4892: LocalDateTime support for Kotlin/Java
    // LocalDateTime.toString() returns ISO-8601 format like "2024-12-25T12:00:00"
    // Note: LocalDateTime has no timezone info, so Qore will interpret it as a local/floating
    // date-time in the current timezone context. Use ZonedDateTime if timezone preservation is needed.
    if (env.isInstanceOf(v, Globals::classLocalDateTime)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodLocalDateTimeToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    // issue #4892: Instant support for Kotlin/Java
    // Instant.toString() returns ISO-8601 format like "2024-12-25T12:00:00Z"
    if (env.isInstanceOf(v, Globals::classInstant)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodInstantToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    // an OffsetDateTime is an absolute date that keeps its offset; ISO_OFFSET_DATE_TIME always writes the seconds,
    // so that the offset is parsed (see ZonedDateTime above)
    if (env.isInstanceOf(v, Globals::classOffsetDateTime)) {
        jvalue arg;
        arg.l = Globals::isoOffsetDateTimeFormatter;
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodOffsetDateTimeFormat, &arg).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    // a time of day is a time on 1970-01-01, as for java.sql.Time; an OffsetTime keeps its offset
    if (env.isInstanceOf(v, Globals::classOffsetTime)) {
        jvalue arg;
        arg.l = Globals::isoOffsetTimeFormatter;
        LocalReference<jstring> time_str = env.callObjectMethod(v,
            Globals::methodOffsetTimeFormat, &arg).as<jstring>();
        Env::GetStringUtfChars chars(env, time_str);
        QoreStringMaker date("1970-01-01T%s", chars.c_str());
        return QoreValue(new DateTimeNode(date.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classLocalTime)) {
        jvalue arg;
        arg.l = Globals::isoLocalTimeFormatter;
        LocalReference<jstring> time_str = env.callObjectMethod(v,
            Globals::methodLocalTimeFormat, &arg).as<jstring>();
        Env::GetStringUtfChars chars(env, time_str);
        QoreStringMaker date("1970-01-01T%s", chars.c_str());
        return QoreValue(new DateTimeNode(date.c_str()));
    }

    // a LocalDate is midnight of that day in the current time zone, as for java.sql.Date
    if (env.isInstanceOf(v, Globals::classLocalDate)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodLocalDateToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    // the classes below are not final: a subclass's Java code can run, in the context of the calling code
    // (qore#6551, see JniPendingContextHelper)
    if (env.isInstanceOf(v, Globals::classTimestamp)) {
        JniPendingContextHelper::set(env);
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodTimestampToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classDate)) {
        JniPendingContextHelper::set(env);
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodDateToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classTime)) {
        JniPendingContextHelper::set(env);
        LocalReference<jstring> time_str = env.callObjectMethod(v,
            Globals::methodTimeToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, time_str);
        QoreStringMaker date("1970-01-01T%s", chars.c_str());
        return QoreValue(new DateTimeNode(date.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classBigDecimal)) {
        JniPendingContextHelper::set(env);
        LocalReference<jstring> num_str = env.callObjectMethod(v,
            Globals::methodBigDecimalToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, num_str);
        switch (numeric) {
            case ENO_NUMERIC:
                return new QoreNumberNode(chars.c_str());
            case ENO_STRING:
                return new QoreStringNode(chars.c_str());
            case ENO_OPTIMAL: {
                const char* dot = strchr(chars.c_str(), '.');
                if (!dot) {
                    errno = 0;
                    int64 num = strtoll(chars.c_str(), 0, 10);
                    if (errno == ERANGE) {
                        return new QoreNumberNode(chars.c_str());
                    }
                    return num;
                }
                SimpleRefHolder<QoreNumberNode> afterDot(new QoreNumberNode(dot + 1));
                if (afterDot->equals(0LL)) {
                    return strtoll(chars.c_str(), 0, 10);
                }
                return new QoreNumberNode(chars.c_str());
            }
            default:
                assert(false);
        }
    }

    // the native handle of a Qore object or closure is held while it is used (qore#6864): a release waits until it is
    // no longer used; qoreJniEnterHandle() raises an IllegalStateException if the handle has been released
    if (env.isInstanceOf(v, Globals::classQoreObjectBase)) {
        JniPendingContextHelper::set(env);
        jlong ptr = env.callLongMethod(v, Globals::methodQoreObjectBaseEnterHandle, nullptr);
        JniHandleUseHelper use(env, v, Globals::methodQoreObjectBaseExitHandle);
        QoreObject* obj = JniObjectHandle::fromJava(ptr)->getObject();
        return obj->refSelf();
    }

    if (env.isInstanceOf(v, Globals::classQoreClosure)) {
        JniPendingContextHelper::set(env);
        jlong ptr = env.callLongMethod(v, Globals::methodQoreClosureEnterHandle, nullptr);
        JniHandleUseHelper use(env, v, Globals::methodQoreClosureExitHandle);
        const JniClosureHandle* handle = reinterpret_cast<const JniClosureHandle*>(ptr);
        return handle->getCall()->refRefSelf();
    }

    if (env.isInstanceOf(v, Globals::classMap) && !JniExternalProgramData::compatTypes()) {
        // create hash from Map; the Map's methods are Java code of its class, which runs in the context of the
        // calling code (qore#6551)
        JniPendingContextHelper::set(env);
        LocalReference<jobject> set = env.callObjectMethod(v,
            Globals::methodMapEntrySet, nullptr);
        if (!set) {
            return QoreValue();
        }
        LocalReference<jobject> i = env.callObjectMethod(set,
            Globals::methodSetIterator, nullptr);
        if (!i) {
            return QoreValue();
        }

        ExceptionSink xsink;

        // issue #4892: First pass - convert all entries and determine common value type
        std::vector<std::pair<std::string, QoreValue>> entries;
        auto entry_value = [](std::pair<std::string, QoreValue>& entry) -> QoreValue& { return entry.second; };
        JniConvertedValuesGuard<decltype(entries), decltype(entry_value)> entries_guard(entries, entry_value);
        qore_type_t common_type = -1;  // -1 means not yet determined
        bool mixed_types = false;

        while (true) {
            if (!env.callBooleanMethod(i, Globals::methodIteratorHasNext, nullptr)) {
                break;
            }

            LocalReference<jobject> element = env.callObjectMethod(i,
                Globals::methodIteratorNext, nullptr);
            if (element) {
                LocalReference<jobject> key = env.callObjectMethod(element,
                    Globals::methodEntryGetKey, nullptr);

                // if key is not a string, then we cannot convert it to Qore
                if (!env.isInstanceOf(key, Globals::classString)) {
                    // Clean up already converted entries
                    for (auto& entry : entries) {
                        entry.second.discard(&xsink);
                    }
                    entries.clear();
                    return qjcm.getValue(v, pgm, compat_types);
                }

                // the key in UTF-8; a hash key cannot have a NUL character, at which it would be cut, so a key with
                // one is refused (qore#6738); it is copied, so that the JNI buffer is released before the value is
                // converted
                std::string key_utf8;
                {
                    Env::GetStringUtfChars key_str(env, key.as<jstring>());
                    jni_check_key(key_str.c_str(), key_str.size(), "Java Map key");
                    key_utf8.assign(key_str.c_str(), key_str.size());
                }

                LocalReference<jobject> value = env.callObjectMethod(element,
                    Globals::methodEntryGetValue, nullptr);

                ValueHolder val(convertToQore(value.release(), pgm, compat_types), &xsink);
                if (xsink) {
                    // Clean up already converted entries
                    for (auto& entry : entries) {
                        entry.second.discard(&xsink);
                    }
                    entries.clear();
                    throw XsinkException(xsink);
                }

                // Track common type
                qore_type_t val_type = val->getType();
                if (common_type == -1) {
                    common_type = val_type;
                } else if (common_type != val_type) {
                    mixed_types = true;
                }

                entries.emplace_back(std::move(key_utf8), val.release());
            }
        }

        // Determine the type info based on common value type
        const QoreTypeInfo* hash_value_type_info = autoTypeInfo;
        if (!mixed_types && !entries.empty()) {
            switch (common_type) {
                case NT_INT:
                    hash_value_type_info = bigIntTypeInfo;
                    break;
                case NT_STRING:
                    hash_value_type_info = stringTypeInfo;
                    break;
                case NT_FLOAT:
                    hash_value_type_info = floatTypeInfo;
                    break;
                case NT_BOOLEAN:
                    hash_value_type_info = boolTypeInfo;
                    break;
                case NT_DATE:
                    hash_value_type_info = dateTypeInfo;
                    break;
                case NT_NUMBER:
                    hash_value_type_info = numberTypeInfo;
                    break;
                case NT_BINARY:
                    hash_value_type_info = binaryTypeInfo;
                    break;
                // For complex types (hash, list, object), keep auto to avoid overly specific typing
                default:
                    hash_value_type_info = autoTypeInfo;
                    break;
            }
        }

        // Create typed hash and add entries
        ReferenceHolder<QoreHashNode> rv(new QoreHashNode(hash_value_type_info), &xsink);
        for (size_t idx = 0; idx < entries.size(); ++idx) {
            // the key is converted from UTF-8 to the default encoding, which hash keys are in
            rv->setKeyValue(QoreString(entries[idx].first, QCS_UTF8), entries[idx].second, &xsink);
            // setKeyValue() takes ownership of the value also when it fails (it discards it then)
            entries[idx].second = QoreValue();
            if (xsink) {
                // the remaining values are discarded here
                for (size_t j = idx + 1; j < entries.size(); ++j) {
                    entries[j].second.discard(&xsink);
                }
                entries.clear();
                throw XsinkException(xsink);
            }
        }
        entries.clear();

        return rv.release();
    }

    if (env.isInstanceOf(v, Globals::classList)) {
        // create list from List; the List's methods are Java code of its class, which runs in the context of the
        // calling code (qore#6551)
        JniPendingContextHelper::set(env);
        jint size = env.callIntMethod(v, Globals::methodListSize, nullptr);

        ExceptionSink xsink;

        // issue #4892: First pass - convert all elements and determine common type
        std::vector<QoreValue> elements;
        auto element_value = [](QoreValue& element) -> QoreValue& { return element; };
        JniConvertedValuesGuard<decltype(elements), decltype(element_value)> elements_guard(elements, element_value);
        elements.reserve(size);
        qore_type_t common_type = -1;  // -1 means not yet determined
        bool mixed_types = false;

        jint pos = 0;
        while (pos < size) {
            std::vector<jvalue> jargs(1);
            jargs[0].i = pos++;

            LocalReference<jobject> value = env.callObjectMethod(v,
                Globals::methodListGet, &jargs[0]);

            ValueHolder val(convertToQore(value.release(), pgm, compat_types), &xsink);
            if (xsink) {
                // Clean up already converted elements
                for (auto& elem : elements) {
                    elem.discard(&xsink);
                }
                elements.clear();
                throw XsinkException(xsink);
            }

            // Track common type
            qore_type_t elem_type = val->getType();
            if (common_type == -1) {
                common_type = elem_type;
            } else if (common_type != elem_type) {
                mixed_types = true;
            }

            elements.push_back(val.release());
        }

        // Determine the type info based on common type
        const QoreTypeInfo* list_type_info = autoTypeInfo;
        if (!mixed_types && !elements.empty()) {
            switch (common_type) {
                case NT_INT:
                    list_type_info = bigIntTypeInfo;
                    break;
                case NT_STRING:
                    list_type_info = stringTypeInfo;
                    break;
                case NT_FLOAT:
                    list_type_info = floatTypeInfo;
                    break;
                case NT_BOOLEAN:
                    list_type_info = boolTypeInfo;
                    break;
                case NT_DATE:
                    list_type_info = dateTypeInfo;
                    break;
                case NT_NUMBER:
                    list_type_info = numberTypeInfo;
                    break;
                case NT_BINARY:
                    list_type_info = binaryTypeInfo;
                    break;
                // For complex types (hash, list, object), keep auto to avoid overly specific typing
                default:
                    list_type_info = autoTypeInfo;
                    break;
            }
        }

        // Create typed list and add elements
        ReferenceHolder<QoreListNode> rv(new QoreListNode(list_type_info), &xsink);
        for (size_t idx = 0; idx < elements.size(); ++idx) {
            rv->push(elements[idx], &xsink);
            // push() takes ownership of the value also when it fails (it discards it then)
            elements[idx] = QoreValue();
            if (xsink) {
                // the remaining values are discarded here
                for (size_t j = idx + 1; j < elements.size(); ++j) {
                    elements[j].discard(&xsink);
                }
                elements.clear();
                throw XsinkException(xsink);
            }
        }
        elements.clear();

        return rv.release();
    }

    // for relative date/time values
    if (env.isInstanceOf(v, Globals::classQoreRelativeTime)) {
        int year = env.getIntField(v, Globals::fieldQoreRelativeTimeYear),
            month = env.getIntField(v, Globals::fieldQoreRelativeTimeMonth),
            day = env.getIntField(v, Globals::fieldQoreRelativeTimeDay),
            hour = env.getIntField(v, Globals::fieldQoreRelativeTimeHour),
            minute = env.getIntField(v, Globals::fieldQoreRelativeTimeMinute),
            second = env.getIntField(v, Globals::fieldQoreRelativeTimeSecond),
            us = env.getIntField(v, Globals::fieldQoreRelativeTimeUs);

        return QoreValue(DateTimeNode::makeRelative(year, month, day, hour, minute, second, us));
    }

    // for Qore closure / call references
    if (env.isInstanceOf(v, Globals::classQoreClosureMarker)) {
        return new QoreJniFunctionalInterface(v);
    }

    return qjcm.getValue(v, pgm, compat_types);
}

} // namespace jni

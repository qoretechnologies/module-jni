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

QoreValue JavaToQore::convertToQore(LocalReference<jobject> v, QoreProgram* pgm, bool compat_types,
        NumericOption numeric) {
    if (!v) {
        return QoreValue();
    }

    Env env;

    // convert to Qore value if possible
    if (env.isInstanceOf(v, Globals::classString)) {
        Env::GetStringUtfChars chars(env, v.as<jstring>());
        return QoreValue(new QoreStringNode(chars.c_str(), QCS_UTF8));
    }

    if (env.isInstanceOf(v, Globals::classZonedDateTime)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodZonedDateTimeToString, nullptr).as<jstring>();
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

    if (env.isInstanceOf(v, Globals::classTimestamp)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodTimestampToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classDate)) {
        LocalReference<jstring> date_str = env.callObjectMethod(v,
            Globals::methodDateToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, date_str);
        return QoreValue(new DateTimeNode(chars.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classTime)) {
        LocalReference<jstring> time_str = env.callObjectMethod(v,
            Globals::methodTimeToString, nullptr).as<jstring>();
        Env::GetStringUtfChars chars(env, time_str);
        QoreStringMaker date("1970-01-01T%s", chars.c_str());
        return QoreValue(new DateTimeNode(date.c_str()));
    }

    if (env.isInstanceOf(v, Globals::classBigDecimal)) {
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

    if (env.isInstanceOf(v, Globals::classQoreObjectBase)) {
        QoreObject* obj = reinterpret_cast<QoreObject*>(env.callLongMethod(v,
            Globals::methodQoreObjectBaseGet, nullptr));
        return obj->refSelf();
    }

    if (env.isInstanceOf(v, Globals::classQoreClosure)) {
        ResolvedCallReferenceNode* call = reinterpret_cast<ResolvedCallReferenceNode*>(env.callLongMethod(v,
            Globals::methodQoreClosureGet, nullptr));
        return call->refRefSelf();
    }

    if (env.isInstanceOf(v, Globals::classMap) && !JniExternalProgramData::compatTypes()) {
        // create hash from Map
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
                    return qjcm.getValue(v, pgm, compat_types);
                }

                LocalReference<jobject> value = env.callObjectMethod(element,
                    Globals::methodEntryGetValue, nullptr);

                ValueHolder val(convertToQore(value.release(), pgm, compat_types), &xsink);
                if (xsink) {
                    // Clean up already converted entries
                    for (auto& entry : entries) {
                        entry.second.discard(&xsink);
                    }
                    throw XsinkException(xsink);
                }

                // Track common type
                qore_type_t val_type = val->getType();
                if (common_type == -1) {
                    common_type = val_type;
                } else if (common_type != val_type) {
                    mixed_types = true;
                }

                Env::GetStringUtfChars key_str(env, key.as<jstring>());
                entries.emplace_back(key_str.c_str(), val.release());
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
            rv->setKeyValue(entries[idx].first.c_str(), entries[idx].second, &xsink);
            if (xsink) {
                // setKeyValue takes ownership on success, but on failure we must discard
                // the current value and all remaining values
                entries[idx].second.discard(&xsink);
                for (size_t j = idx + 1; j < entries.size(); ++j) {
                    entries[j].second.discard(&xsink);
                }
                throw XsinkException(xsink);
            }
        }

        return rv.release();
    }

    if (env.isInstanceOf(v, Globals::classList)) {
        // create list from List
        jint size = env.callIntMethod(v, Globals::methodListSize, nullptr);

        ExceptionSink xsink;

        // issue #4892: First pass - convert all elements and determine common type
        std::vector<QoreValue> elements;
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
            if (xsink) {
                // push takes ownership on success, but on failure we must discard
                // the current value and all remaining values
                elements[idx].discard(&xsink);
                for (size_t j = idx + 1; j < elements.size(); ++j) {
                    elements[j].discard(&xsink);
                }
                throw XsinkException(xsink);
            }
        }

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

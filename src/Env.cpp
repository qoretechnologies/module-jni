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
///
/// \file
/// \brief Defines the Env class implementation.
///
//------------------------------------------------------------------------------

#include "defs.h"
#include "QoreJniClassMap.h"

#include <qore/Qore.h>

using namespace jni;

// appends a code point to a UTF-16 string
static void utf16_append(std::vector<jchar>& out, uint32_t cp) {
    if (cp >= 0x10000) {
        cp -= 0x10000;
        out.push_back(static_cast<jchar>(0xd800 + (cp >> 10)));
        out.push_back(static_cast<jchar>(0xdc00 + (cp & 0x3ff)));
    } else {
        out.push_back(static_cast<jchar>(cp));
    }
}

// appends a code point to a UTF-8 string
static void utf8_append(std::string& out, uint32_t cp) {
    if (cp < 0x80) {
        out += static_cast<char>(cp);
    } else if (cp < 0x800) {
        out += static_cast<char>(0xc0 | (cp >> 6));
        out += static_cast<char>(0x80 | (cp & 0x3f));
    } else if (cp < 0x10000) {
        out += static_cast<char>(0xe0 | (cp >> 12));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3f));
        out += static_cast<char>(0x80 | (cp & 0x3f));
    } else {
        out += static_cast<char>(0xf0 | (cp >> 18));
        out += static_cast<char>(0x80 | ((cp >> 12) & 0x3f));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3f));
        out += static_cast<char>(0x80 | (cp & 0x3f));
    }
}

void Env::decodeUtf8(const char* utf8, size_t len, std::vector<jchar>& out) {
    out.reserve(len);
    const unsigned char* p = reinterpret_cast<const unsigned char*>(utf8);
    const unsigned char* e = p + len;
    while (p < e) {
        unsigned char c = *p;
        if (c < 0x80) {
            out.push_back(c);
            ++p;
            continue;
        }
        // the length of the sequence, the bits of its first byte and the smallest code point it may encode
        size_t n;
        uint32_t cp;
        uint32_t min;
        if (c >= 0xc2 && c <= 0xdf) {
            n = 2;
            cp = c & 0x1f;
            min = 0x80;
        } else if ((c & 0xf0) == 0xe0) {
            n = 3;
            cp = c & 0x0f;
            min = 0x800;
        } else if (c >= 0xf0 && c <= 0xf4) {
            n = 4;
            cp = c & 0x07;
            min = 0x10000;
        } else {
            // a continuation byte, an overlong 2-byte lead (C0, C1) or a byte that never appears in UTF-8
            utf16_append(out, 0xfffd);
            ++p;
            continue;
        }
        size_t i = 1;
        for (; i < n && p + i < e; ++i) {
            if ((p[i] & 0xc0) != 0x80) {
                break;
            }
            cp = (cp << 6) | (p[i] & 0x3f);
        }
        if (i < n || cp < min || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) {
            // a truncated sequence (its valid bytes are skipped), or an overlong form, a value beyond Unicode or an
            // encoded surrogate (CESU-8): one U+FFFD
            utf16_append(out, 0xfffd);
            p += i;
            continue;
        }
        utf16_append(out, cp);
        p += n;
    }
}

void Env::appendUtf16(std::string& out, const jchar* utf16, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        uint32_t u = utf16[i];
        if (u >= 0xd800 && u < 0xdc00 && i + 1 < len && utf16[i + 1] >= 0xdc00 && utf16[i + 1] < 0xe000) {
            utf8_append(out, 0x10000 + ((u - 0xd800) << 10) + (utf16[i + 1] - 0xdc00));
            ++i;
        } else if (u >= 0xd800 && u < 0xe000) {
            // an unpaired surrogate is not a character
            utf8_append(out, 0xfffd);
        } else {
            utf8_append(out, u);
        }
    }
}

bool Env::convertModifiedUtf8(const char* mutf8, std::string& out) {
    const unsigned char* p = reinterpret_cast<const unsigned char*>(mutf8);
    // a surrogate is a 3-byte sequence starting with 0xed 0xa0..0xbf; NUL is the two bytes C0 80
    const unsigned char* q = p;
    for (; *q; ++q) {
        if ((q[0] == 0xed && (q[1] & 0xe0) == 0xa0) || (q[0] == 0xc0 && q[1] == 0x80)) {
            break;
        }
    }
    if (!*q) {
        return false;
    }
    out.assign(mutf8, q - p);
    // returns the UTF-16 unit of a 3-byte sequence
    auto unit = [](const unsigned char* s) -> uint32_t {
        return ((s[0] & 0x0f) << 12) | ((s[1] & 0x3f) << 6) | (s[2] & 0x3f);
    };
    while (*q) {
        if (q[0] == 0xc0 && q[1] == 0x80) {
            out += '\0';
            q += 2;
            continue;
        }
        if (q[0] == 0xed && (q[1] & 0xe0) == 0xa0 && q[2]) {
            uint32_t u = unit(q);
            if (u < 0xdc00 && q[3] == 0xed && (q[4] & 0xf0) == 0xb0 && q[5]) {
                // a high surrogate followed by a low one: one character in a 4-byte sequence
                utf8_append(out, 0x10000 + ((u - 0xd800) << 10) + (unit(q + 3) - 0xdc00));
                q += 6;
                continue;
            }
            // an unpaired surrogate is not a character
            utf8_append(out, 0xfffd);
            q += 3;
            continue;
        }
        out += static_cast<char>(*q);
        ++q;
    }
    return true;
}

Env::Env(bool set_context) {
   // Check for interrupt before attaching to JVM
   {
      ExceptionSink xsink;
      if (qore_check_cancel(&xsink, "JVM attach")) {
         throw XsinkException(xsink);
      }
   }

   bool new_attach;
   env = Jvm::attachAndGetEnv(new_attach);
   if (set_context && new_attach)
      JniExternalProgramData::setContext(*this);
}

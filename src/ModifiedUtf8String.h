//--------------------------------------------------------------------*- C++ -*-
//
//  Qore Programming Language
//
//  Copyright (C) 2016 - 2022 Qore Technologies, s.r.o.
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
/// \brief Defines the ModifiedUtf8String class.
///
//------------------------------------------------------------------------------
#ifndef QORE_JNI_MODIFIEDUTF8STRING_H_
#define QORE_JNI_MODIFIEDUTF8STRING_H_

#include <qore/Qore.h>

namespace jni {

/**
 * \brief A helper class for converting a string to UTF-8 for Java.
 *
 * The string is converted to UTF-8 only: Env::newString() makes a Java string of it (strictly, from UTF-16, for any
 * string that is not ASCII), and a class name for JNI's FindClass() is ASCII in practice.
 */
class ModifiedUtf8String {

public:
   /**
    * \brief Converts the source string.
    *
    * Raises an exception if the string cannot be converted
    * \param src the source string to convert, must not be null
    * \throws StringException if the string cannot be converted
    */
   explicit ModifiedUtf8String(const QoreString& src) {
      ExceptionSink xsink;
      helper.set(&src, QCS_UTF8, &xsink);
      if (xsink) {
         throw XsinkException(xsink);
      }
   }

   explicit ModifiedUtf8String(const QoreString* src) {
      ExceptionSink xsink;
      helper.set(src, QCS_UTF8, &xsink);
      if (xsink) {
         throw XsinkException(xsink);
      }
   }

   /**
    * \brief Returns the string converted to UTF-8.
    *
    * The returned pointer remains valid until this instance is destroyed.
    * \return the string converted to UTF-8
    */
   const char* c_str() {
      return helper->getBuffer();
   }

   //! Returns the length of the string in bytes; it may have NUL characters
   size_t size() {
      return helper->size();
   }

private:
   TempEncodingHelper helper;
};

} // namespace jni

#endif // QORE_JNI_MODIFIEDUTF8STRING_H_

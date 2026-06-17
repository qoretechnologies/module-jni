/** Java ClassFileLocator backed by a QoreURLClassLoader

    Qore Programming Language

    Copyright 2016 - 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a
    copy of this software and associated documentation files (the "Software"),
    to deal in the Software without restriction, including without limitation
    the rights to use, copy, modify, merge, publish, distribute, sublicense,
    and/or sell copies of the Software, and to permit persons to whom the
    Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
    FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
    DEALINGS IN THE SOFTWARE.
*/

package org.qore.jni;

import java.util.concurrent.ConcurrentHashMap;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.TypeValidation;

//! A Byte Buddy {@link ClassFileLocator} that supplies class file bytecode from a
//! {@link QoreURLClassLoader}.
/** This is used to describe a class's superclass chain to a Byte Buddy {@code TypePool} from
    bytecode during dynamic class generation, instead of describing it via reflection on loaded
    classes.  Reflection-based descriptions force the JVM to eagerly resolve every type referenced
    in a superclass's method signatures, which - when one of those types is a subclass currently
    being generated - re-enters generation of the in-progress class and fails with
    "&lt;class&gt; is already being created".  Resolving from bytecode avoids that forced resolution.

    <p>When a referenced dynamic class cannot be supplied because it is part of the
    strongly-connected component currently being generated (a base class declaring a method whose
    parameter type is a not-yet-generated subclass), a minimal name-only stub is returned.  Byte
    Buddy only needs such a type's erasure name to compute the signature token of the base method
    while resolving the super constructor; the method is then filtered out as a non-constructor, so
    the stub is never used beyond its name and never reaches {@code defineClass}.  The real class is
    generated normally once the generation cycle has unwound.

    <p>This class deliberately lives outside the bootstrap class set: it references Byte Buddy types,
    which are not available when the bootstrap {@link QoreURLClassLoader} is first loaded.  It is
    only loaded later, when dynamic class generation runs and Byte Buddy is available.

    @see QoreURLClassLoader#getClassFileBytes(String)
*/
public class QoreClassFileLocator implements ClassFileLocator {
    //! cache of generated name-only stub class files, keyed by binary name
    private static final ConcurrentHashMap<String, byte[]> stubCache = new ConcurrentHashMap<String, byte[]>();

    //! the class loader supplying bytecode
    private final QoreURLClassLoader loader;

    //! creates the locator for the given class loader
    public QoreClassFileLocator(QoreURLClassLoader loader) {
        this.loader = loader;
    }

    //! returns the class file bytecode for the given binary name
    /** Falls back to a minimal name-only stub for dynamic classes that cannot currently be
        supplied because they are mid-generation; returns an "illegal" resolution otherwise.
    */
    @Override
    public Resolution locate(String name) {
        byte[] byte_code = loader.getClassFileBytes(name);
        if (byte_code != null) {
            return new Resolution.Explicit(byte_code);
        }
        // A dynamic class that cannot be supplied here is necessarily part of the generation
        // cycle currently in progress (a non-in-progress dynamic class would generate
        // successfully); return a name-only stub sufficient for signature-token resolution.
        if (QoreURLClassLoader.isDynamic(name)) {
            return new Resolution.Explicit(getStub(name));
        }
        return new Resolution.Illegal(name);
    }

    //! returns (creating and caching if necessary) a minimal name-only stub class file
    private static byte[] getStub(String name) {
        return stubCache.computeIfAbsent(name, n -> new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class)
            .name(n)
            .make()
            .getBytes());
    }

    //! no-op; the underlying class loader is not owned by this locator
    @Override
    public void close() {
    }
}

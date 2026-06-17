
package org.qore.jni;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import java.nio.file.Files;
import java.nio.file.Path;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.description.type.TypeVariableToken;
import net.bytebuddy.description.modifier.MethodArguments;
import net.bytebuddy.description.method.MethodDescription.Token;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.matcher.ElementMatchers;

import org.qore.jni.QoreURLClassLoader;
import org.qore.jni.QoreJavaObjectPtr;

/** Helper class for building dynamic Java classes
 */
public class JavaClassBuilder {
    private static Class objArray;
    private static Method mStaticCall;
    private static Method mNormalCall;
    private static Method mFunctionCall;
    private static Method mGetConstantValue;
    // Class-identity fields embedded in bytecode at class generation time.
    //
    // $qore_cls_ptr (long) — raw QoreClass* used by the constructor delegation
    // chain (QoreJavaClassBase(long cptr, ...) → QoreObjectBase) at object
    // instantiation time.  Kept as-is because instantiation can only succeed
    // when the canonical owner Program is alive (the Java code path leading
    // there pins it), so the pointer is valid in this narrow scope.
    //
    // $qore_cls_pgm_id (long) + $qore_cls_path (String) — added for safe
    // late-read paths (tryGetQoreClass, getQoreType).  Earlier code embedded
    // only the raw QoreClass* and dereferenced it from arbitrary contexts
    // (parent-class walks during a different program's class definition,
    // type-info queries during reflection, etc.); when the canonical owner
    // had been destroyed but the canonical_loader_cache still held the
    // class, the read returned freed memory and equal()/inHierarchy()
    // matched against garbage.  By embedding the (programId, qpath) tuple
    // and resolving via QoreProgram::resolveProgramId() + findClass() at
    // every late read, we get either the current QoreClass at that path
    // or a clean nullptr (caller throws cleanly) when the owner is dead.
    //
    // programId is stable: assigned monotonically, never reused across
    // program destruction.  qpath is the Qore-namespace-qualified path
    // (e.g. "::OMQ::UserApi::Job::QorusJob") and serves as the
    // identity-preserving lookup key — findClass() across import chains
    // yields classes with the same classID, so equal() between the
    // resolved class and any imported copy succeeds via classID match.
    private static final String CLASS_FIELD = "$qore_cls_ptr";
    private static final String CLASS_PGM_ID_FIELD = "$qore_cls_pgm_id";
    private static final String CLASS_PATH_FIELD = "$qore_cls_path";

    // copied from org.objectweb.asm.Opcodes
    public static final int ACC_PUBLIC    = (1 << 0);
    public static final int ACC_PRIVATE   = (1 << 1);
    public static final int ACC_PROTECTED = (1 << 2);
    public static final int ACC_STATIC    = (1 << 3);
    public static final int ACC_FINAL     = (1 << 4);
    public static final int ACC_ABSTRACT  = (1 << 10);

    //! static initialization
    static {
        try {
            objArray = Class.forName("[L" + Object.class.getCanonicalName() + ";");

            Class<?>[] args = new Class<?>[6];
            args[0] = String.class;
            args[1] = Long.TYPE;
            args[2] = Long.TYPE;
            args[3] = Long.TYPE;
            args[4] = Long.TYPE;
            args[5] = objArray;
            mStaticCall = JavaClassBuilder.class.getDeclaredMethod("doStaticCall", args);

            args = new Class<?>[5];
            args[0] = String.class;
            args[1] = Long.TYPE;
            args[2] = Long.TYPE;
            args[3] = Long.TYPE;
            args[4] = objArray;
            mNormalCall = JavaClassBuilder.class.getDeclaredMethod("doNormalCall", args);

            args = new Class<?>[4];
            args[0] = Long.TYPE;
            args[1] = Long.TYPE;
            args[2] = Long.TYPE;
            args[3] = objArray;
            mFunctionCall = JavaClassBuilder.class.getDeclaredMethod("doFunctionCall", args);

            args = new Class<?>[2];
            args[0] = Long.TYPE;
            args[1] = Long.TYPE;
            mGetConstantValue = JavaClassBuilder.class.getDeclaredMethod("getConstantValue", args);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! Returns a builder object for a dynamic class mapping Qore functions to static Java methods
    public static DynamicType.Builder<?> getFunctionConstantClassBuilder(String bin_name) throws NoSuchMethodException {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .with(new NamingStrategy.AbstractBase() {
                @Override
                public String name(TypeDescription superClass) {
                    return bin_name;
                }
            })
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .modifiers(ACC_PUBLIC);
    }

    //! Add a function to a function class
    public static DynamicType.Builder<?> addFunction(DynamicType.Builder<?> bb, String functionName, long pgm,
            long fptr, long vptr, TypeDefinition returnType, List<TypeDefinition> paramTypes, boolean varargs) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<TypeDefinition>();
        }

        DynamicType.Builder.MethodDefinition.ExceptionDefinition<?> eb =
            varargs
                ? bb.defineMethod(functionName, returnType, Visibility.PUBLIC, Ownership.STATIC,
                    MethodArguments.VARARGS)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class)
                : bb.defineMethod(functionName, returnType, Visibility.PUBLIC, Ownership.STATIC)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class);

        return (DynamicType.Builder<?>)eb.intercept(
                MethodCall.invoke(mFunctionCall)
                    .with(pgm)
                    .with(fptr)
                    .with(vptr)
                    .withArgumentArray()
                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
            );
    }

    //! Add a field to a class
    public static DynamicType.Builder<?> addStaticField(DynamicType.Builder<?> bb, String fieldName, int modifiers,
            TypeDescription fieldType, long cPtr, ArrayList<StaticEntry> staticList) {
        modifiers |= ACC_FINAL | ACC_STATIC;
        bb = bb.defineField(fieldName, fieldType, modifiers);

        staticList.add(new StaticEntry(fieldName, modifiers, fieldType, cPtr));
        return bb;
    }

    //! Creates the static initializer for a class
    public static DynamicType.Builder<?> createStaticInitializer(DynamicType.Builder<?> bb, String className,
            long pgm, ArrayList<StaticEntry> staticList) {
        Implementation.Composable mc = null;
        for (StaticEntry entry : staticList) {
            Implementation.Composable new_mc = MethodCall.invoke(mGetConstantValue)
                .with(pgm)
                .with(entry.cPtr)
                .setsField(ElementMatchers.is(
                    new FieldDescription.Latent(
                        InstrumentedType.Default.of(className, null, Modifier.PUBLIC
                    ),
                    new FieldDescription.Token(
                        entry.fieldName,
                        entry.modifiers,
                        new TypeDescription.Generic.OfNonGenericType.Latent(entry.fieldType, null))
                    )
                ))
                .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
            if (mc == null) {
                mc = new_mc;
            } else {
                mc = mc.andThen(new_mc);
            }
        }

        if (mc == null) {
            return bb;
        }

        return bb.invokable(ElementMatchers.isTypeInitializer())
            .intercept(mc);
    }

    //! Returns a builder object for a dynamic class
    public static DynamicType.Builder<?> getClassBuilder(String className, Class<?> parentClass,
            TypeDefinition parentType, ArrayList<TypeDefinition> interfaces, ArrayList<String> typeParams,
            boolean is_abstract, long cptr, long cls_pgm_id, String cls_path,
            ClassLoader genLoader) throws NoSuchMethodException {
        // Describe the superclass from bytecode rather than as a loaded type.  A loaded
        // superclass description forces the JVM (via reflection in Byte Buddy's method-graph
        // and super-constructor resolution) to eagerly resolve every type referenced in the
        // superclass's method signatures; when one of those is a subclass currently being
        // generated, that re-enters generation and fails with "<class> is already being
        // created".  Reading the superclass via a TypePool avoids that forced resolution.
        // Generic type arguments (if any) are preserved by re-wrapping the bytecode-backed
        // raw type.
        TypeDescription rawSuper = getTypeDescriptionFromPool(parentClass, genLoader);
        TypeDefinition superType;
        if (parentType != null && parentType.asGenericType().getSort().isParameterized()) {
            superType = TypeDescription.Generic.Builder.parameterizedType(rawSuper,
                parentType.asGenericType().getTypeArguments()).build();
        } else {
            superType = rawSuper;
        }

        DynamicType.Builder<?> bb;
        bb = new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .with(new NamingStrategy.AbstractBase() {
                @Override
                public String name(TypeDescription superClass) {
                    return className;
                }
            })
            .subclass(superType, ConstructorStrategy.Default.NO_CONSTRUCTORS);

        if (typeParams != null) {
            for (String typeParam : typeParams) {
                bb = bb.typeVariable(typeParam);
            }
        }

        // add interfaces to class
        if (interfaces != null) {
            for (TypeDefinition t : interfaces) {
                bb = bb.implement(t);
            }
        }

        int modifiers = ACC_PUBLIC;
        if (is_abstract) {
            modifiers |= ACC_ABSTRACT;
        }

        bb = bb.modifiers(modifiers);

        // add a static field for storing the class ptr (used in constructor
        // delegation chain, where the QoreClass is alive by construction)
        bb = (DynamicType.Builder<?>)bb.defineField(CLASS_FIELD, Long.TYPE,
            Modifier.FINAL | Modifier.PUBLIC | Modifier.STATIC)
            .value(cptr);

        // add static fields for late-read class identity resolution
        // (read by tryGetQoreClass / getQoreType): programId of canonical
        // owner + qpath, used to look up a live QoreClass at access time
        // even if the original generator's class instance has been freed
        bb = (DynamicType.Builder<?>)bb.defineField(CLASS_PGM_ID_FIELD, Long.TYPE,
            Modifier.FINAL | Modifier.PUBLIC | Modifier.STATIC)
            .value(cls_pgm_id);
        bb = (DynamicType.Builder<?>)bb.defineField(CLASS_PATH_FIELD, String.class,
            Modifier.FINAL | Modifier.PUBLIC | Modifier.STATIC)
            .value(cls_path);

        // add default constructor for already-created Qore objects
        ArrayList<Type> paramTypes = new ArrayList<Type>();
        paramTypes.add(QoreJavaObjectPtr.class);
        bb = (DynamicType.Builder<?>)bb.defineConstructor(Visibility.PUBLIC)
            .withParameters(paramTypes)
            .intercept(
                MethodCall.invoke(parentClass.getConstructor(QoreJavaObjectPtr.class))
                    .onSuper()
                    .withArgument(0)
            );

        // add default constructor for dynamic creation from Qore
        paramTypes = new ArrayList<Type>();
        paramTypes.add(Long.TYPE);
        paramTypes.add(Long.TYPE);
        paramTypes.add(Long.TYPE);
        paramTypes.add(objArray);

        return (DynamicType.Builder<?>)bb.defineConstructor(Visibility.PUBLIC)
            .withParameters(paramTypes)
            .throwing(Throwable.class)
            .intercept(
                MethodCall.invoke(parentClass.getConstructor(Long.TYPE, Long.TYPE, Long.TYPE, objArray))
                    .onSuper()
                    .withAllArguments()
            );
    }

    //! add a constructor
    public static DynamicType.Builder<?> addConstructor(DynamicType.Builder<?> bb, Class<?> parentClass,
            long mptr, long vptr, int visibility, List<TypeDefinition> paramTypes, boolean varargs) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<TypeDefinition>();
        }

        DynamicType.Builder.MethodDefinition.ExceptionDefinition<?> eb = varargs
                ? bb.defineConstructor(getVisibility(visibility), MethodArguments.VARARGS)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class)
                : bb.defineConstructor(getVisibility(visibility))
                    .withParameters(paramTypes)
                    .throwing(Throwable.class);

        try {
            if (paramTypes.size() == 0) {
                return (DynamicType.Builder<?>)eb.intercept(
                        MethodCall.invoke(parentClass.getConstructor(Long.TYPE, Long.TYPE, Long.TYPE, objArray))
                            .onSuper()
                            .withField(CLASS_FIELD)
                            .with(mptr)
                            .with(vptr)
                            .with((Object)null)
                );
            }

            return (DynamicType.Builder<?>)eb.intercept(
                    MethodCall.invoke(parentClass.getConstructor(Long.TYPE, Long.TYPE, Long.TYPE, objArray))
                        .onSuper()
                        .withField(CLASS_FIELD)
                        .with(mptr)
                        .with(vptr)
                        .withArgumentArray()
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    //! add normal method
    public static DynamicType.Builder<?> addNormalMethod(DynamicType.Builder<?> bb, String methodName, long mptr,
            long vptr, int visibility, TypeDefinition returnType, List<TypeDefinition> paramTypes, boolean isAbstract,
            boolean varargs) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<TypeDefinition>();
        }

        DynamicType.Builder.MethodDefinition.ExceptionDefinition<?> eb =
            varargs
                ? bb.defineMethod(methodName, returnType, getVisibility(visibility), Ownership.MEMBER,
                    MethodArguments.VARARGS)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class)
                : bb.defineMethod(methodName, returnType, getVisibility(visibility), Ownership.MEMBER)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class);

        if (isAbstract) {
            try {
                bb = (DynamicType.Builder<?>)eb.withoutCode();
            } catch (Throwable e) {
                //System.out.println(e.toString());
                throw e;
            }
        } else if (paramTypes.size() == 0) {
            bb = (DynamicType.Builder<?>)eb.intercept(
                    MethodCall.invoke(mNormalCall)
                    .with(methodName)
                    .withField("obj")
                    .with(mptr)
                    .with(vptr)
                    .with((Object)null)
                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
                );
        } else {
            bb = (DynamicType.Builder<?>)eb.intercept(
                    MethodCall.invoke(mNormalCall)
                    .with(methodName)
                    .withField("obj")
                    .with(mptr)
                    .with(vptr)
                    .withArgumentArray()
                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
            );
        }
        return bb;
    }

    //! add static method
    public static DynamicType.Builder<?> addStaticMethod(DynamicType.Builder<?> bb, String methodName, long pgm, long mptr,
            long vptr, int visibility, TypeDefinition returnType, List<TypeDefinition> paramTypes, boolean varargs) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<TypeDefinition>();
        }

        DynamicType.Builder.MethodDefinition.ExceptionDefinition<?> eb =
            varargs
                ? bb.defineMethod(methodName, returnType, getVisibility(visibility), Ownership.STATIC,
                    MethodArguments.VARARGS)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class)
                : bb.defineMethod(methodName, returnType, getVisibility(visibility), Ownership.STATIC)
                    .withParameters(paramTypes)
                    .throwing(Throwable.class);

        return (DynamicType.Builder<?>)eb.intercept(
                MethodCall.invoke(mStaticCall)
                .with(methodName)
                .withField(CLASS_FIELD)
                .with(pgm)
                .with(mptr)
                .with(vptr)
                .withArgumentArray()
                .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
            );
    }

    @SuppressWarnings("unchecked")
    public static byte[] getByteCodeFromBuilder(DynamicType.Builder<?> bb, QoreURLClassLoader classLoader) {
        return bb.make().getBytes();
    }

    /** makes a static method call
     *
     * @param methodName the name of the method
     * @param qclsptr the class pointer
     * @param mptr the method pointer
     * @param vptr the variant pointer
     * @param args the arguments to the call, if any, can be null
     * @return the result of the call
     * @throws Throwable any exception thrown in Qore
     */
    @RuntimeType
    public static Object doStaticCall(String methodName, long qclsptr, long pgm, long mptr, long vptr,
            @Argument(0) Object... args) throws Throwable {
        //System.out.println(String.format("JavaClassBuilder::doStaticCall() %s() cptr: %d args: %s", methodName,
        //  qclsptr, Arrays.toString(args)));
        return doStaticCall0(methodName, qclsptr, pgm, mptr, vptr, args);
    }

    /** makes a normal method call
     *
     * @param methodName the name of the method
     * @param qobjptr the pointer to the Qore object
     * @param mptr the pointer to the Qore method object
     * @param vptr the pointer to the method variant object
     * @param args the arguments to the call, if any, can be null
     *
     * @return the result of the call
     *
     * @throws Throwable any exception thrown in Qore
     */
    @RuntimeType
    public static Object doNormalCall(String methodName, long qobjptr, long mptr, long vptr,
            @Argument(0) Object... args) throws Throwable {
        //System.out.println(String.format("JavaClassBuilder::doNormalCall() %s() ptr: %d args: %s", methodName,
        //  qobjptr, Arrays.toString(args)));
        return doNormalCall0(methodName, qobjptr, mptr, vptr, args);
    }

    /** makes a function call
     *
     * @param fptr the pointer to the Qore function object
     * @param vptr the pointer to the method variant object
     * @param args the arguments to the call, if any, can be null
     *
     * @return the result of the call
     *
     * @throws Throwable any exception thrown in Qore
     */
    @RuntimeType
    public static Object doFunctionCall(long pgm, long fptr, long vptr, @Argument(0) Object... args)
            throws Throwable {
        //System.out.println(String.format("JavaClassBuilder::doFunctionCall() %s() args: %s", methodName,
        //  Arrays.toString(args)));
        return doFunctionCall0(pgm, fptr, vptr, args);
    }

    /** retrieves the value of a constant from the given Qore program
     *
     * @param pgm the pointer to the Qore program object
     * @param cPtr the pointer the constant entry
     *
     * @return the value of the given constant
     */
    @RuntimeType
    public static Object getConstantValue(long pgm, long cPtr) throws Throwable {
        return getConstantValue0(pgm, cPtr);
    }

    /** Returns a TypeDescription object for the given class
     *
     * @param cls the class to return a TypeDescription for
     */
    public static TypeDescription getTypeDescription(Class<?> cls) {
        return new TypeDescription.ForLoadedType(cls);
    }

    /** Returns a bytecode-backed (non-loaded) TypeDescription for the given class.
     *
     * Unlike {@link #getTypeDescription(Class)} (which returns a {@code ForLoadedType} whose
     * {@code getDeclaredMethods()} reflects via {@code Class.getDeclaredMethods0} and thereby
     * forces the JVM to eagerly resolve every parameter/return type in the class's method
     * table), this reads the class file through a {@link TypePool}.  Method param/return types
     * are returned as name-based ({@code Latent}) descriptions and are NOT resolved.  This is
     * used for the superclass when generating a subclass so that Byte Buddy can compute the
     * method graph and super-constructor invocation without recursively loading types that may
     * be mid-generation (which would fail with "<class> is already being created").
     *
     * @param cls the class to describe from bytecode
     * @param genLoader the QoreURLClassLoader driving generation, used as the ClassFileLocator
     */
    public static TypeDescription getTypeDescriptionFromPool(Class<?> cls, ClassLoader genLoader) {
        // Resolve the class (and its full superclass chain) from bytecode via a TypePool.  A
        // QoreURLClassLoader is itself a ClassFileLocator (via QoreClassFileLocator) that can
        // supply bytecode for any class the chain may reach (generated dynamic classes, internal
        // jni-module classes such as QoreJavaClassBase, bundled Byte Buddy classes, JDK classes,
        // and dynamic classes generated on demand).  Fall back to the parent class's loader /
        // system loader.
        ClassFileLocator locator;
        if (genLoader instanceof QoreURLClassLoader) {
            locator = new QoreClassFileLocator((QoreURLClassLoader) genLoader);
        } else {
            ClassLoader parentLoader = cls.getClassLoader();
            locator = (parentLoader instanceof QoreURLClassLoader)
                ? new QoreClassFileLocator((QoreURLClassLoader) parentLoader)
                : ClassFileLocator.ForClassLoader.of(parentLoader != null ? parentLoader
                    : ClassLoader.getSystemClassLoader());
        }
        return TypePool.Default.of(locator).describe(cls.getName()).resolve();
    }

    /** Returns a TypeDescription for a future type based on the binary name
     *
     * @param future_name The binary name of the type to be created
     */
    public static TypeDescription getTypeDescription(String future_name) {
        return InstrumentedType.Default.of(future_name, null, Modifier.PUBLIC);
    }

    /** Returns a TypeDescription for a future generic type based on the binary name
     *
     * @param future_name The binary name of the type to be created
     * @param typeParams The generic type parameter names declared by the future type
     */
    public static TypeDescription getTypeDescription(String future_name, ArrayList<String> typeParams) {
        InstrumentedType type = InstrumentedType.Default.of(future_name, null, Modifier.PUBLIC);
        if (typeParams != null) {
            for (String typeParam : typeParams) {
                if (typeParam != null && !typeParam.isEmpty()) {
                    type = type.withTypeVariable(new TypeVariableToken(typeParam, Collections.emptyList()));
                }
            }
        }
        return type;
    }

    /** Returns a symbolic type variable for generic Java signatures
     *
     * @param name The type variable name
     */
    public static TypeDefinition getTypeVariable(String name) {
        return TypeDescription.Generic.Builder.typeVariable(name).build();
    }

    /** Returns a parameterized type for generic Java signatures
     *
     * @param rawType The raw class type
     * @param typeArgs The generic type arguments
     */
    public static TypeDefinition getParameterizedType(TypeDescription rawType, List<TypeDefinition> typeArgs) {
        if (typeArgs == null || typeArgs.size() == 0) {
            return rawType;
        }
        return TypeDescription.Generic.Builder.parameterizedType(rawType, typeArgs).build();
    }

    private static String getErasureName(Type type) {
        if (type instanceof Class) {
            return ((Class)type).getCanonicalName();
        } else if (type instanceof java.lang.reflect.ParameterizedType) {
            return getErasureName(((java.lang.reflect.ParameterizedType)type).getRawType());
        } else if (type instanceof java.lang.reflect.TypeVariable) {
            Type[] bounds = ((java.lang.reflect.TypeVariable)type).getBounds();
            return bounds.length == 0 ? Object.class.getCanonicalName() : getErasureName(bounds[0]);
        } else if (type instanceof java.lang.reflect.WildcardType) {
            Type[] bounds = ((java.lang.reflect.WildcardType)type).getUpperBounds();
            return bounds.length == 0 ? Object.class.getCanonicalName() : getErasureName(bounds[0]);
        } else if (type instanceof java.lang.reflect.GenericArrayType) {
            String component = getErasureName(((java.lang.reflect.GenericArrayType)type).getGenericComponentType());
            try {
                return Class.forName("[L" + component + ";").getCanonicalName();
            } catch (ClassNotFoundException e) {
                return Object[].class.getCanonicalName();
            }
        }
        return type.getTypeName();
    }

    private static boolean sameErasure(Type javaType, TypeDefinition generatedType) {
        return generatedType.asErasure().getCanonicalName().equals(getErasureName(javaType));
    }

    /** Check if a parent class has a final method with matching name and parameters.
     *
     *  This prevents creating Java methods that would override final methods in parent classes
     *  (e.g., wait(), notify(), notifyAll(), getClass() from java.lang.Object), which would cause
     *  IncompatibleClassChangeError at class load time.
     *
     *  @return true if the parent class has a final method with matching name and params
     */
    public static boolean isFinalBaseClassMethod(Class<?> parentClass, String name, List<TypeDefinition> params) {
        for (Method m : parentClass.getMethods()) {
            if (!m.getName().equals(name) || !Modifier.isFinal(m.getModifiers())) {
                continue;
            }
            // check params
            Type[] mparams = m.getGenericParameterTypes();
            if (mparams == null || mparams.length == 0) {
                if (params == null || params.size() == 0) {
                    return true;
                }
                continue;
            }
            if (params == null || mparams.length != params.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < mparams.length; ++i) {
                if (!sameErasure(mparams[i], params.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    /** Check a class for methods matching a name an TypeDescription list
     *
     */
    public static boolean findBaseClassMethodConflict(Class<?> parentClass, String name, List<TypeDefinition> params,
            boolean check_static) {
        for (Method m : parentClass.getMethods()) {
            if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers()) != check_static) {
                continue;
            }
            // check params
            Type[] mparams = m.getGenericParameterTypes();
            if (mparams == null || mparams.length == 0) {
                if (params == null || params.size() == 0) {
                    return true;
                }
                continue;
            }
            if (mparams.length != params.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < mparams.length; ++i) {
                if (!sameErasure(mparams[i], params.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    static private Visibility getVisibility(int visibility) {
        switch (visibility) {
            case ACC_PUBLIC:
                return Visibility.PUBLIC;
            case ACC_PROTECTED:
                return Visibility.PROTECTED;
            default:
                break;
        }

        return Visibility.PRIVATE;
    }

    private static native Object doStaticCall0(String methodName, long qclsptr, long pgm, long mptr, long vptr,
            Object... args) throws Throwable;
    private static native Object doNormalCall0(String methodName, long qobjptr, long mptr, long vptr, Object... args)
            throws Throwable;
    private static native Object doFunctionCall0(long pgm, long fptr, long vptr, @Argument(0) Object... args)
            throws Throwable;
    private static native Object getConstantValue0(long pgm, long cPtr) throws Throwable;
}

class StaticEntry {
    public String fieldName;
    public int modifiers;
    public TypeDescription fieldType;
    public long cPtr;

    StaticEntry(String fieldName, int modifiers, TypeDescription fieldType, long cPtr) {
        this.fieldName = fieldName;
        this.modifiers = modifiers;
        this.fieldType = fieldType;
        this.cPtr = cPtr;
    }
}

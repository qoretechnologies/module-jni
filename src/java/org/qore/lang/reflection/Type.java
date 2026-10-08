/** Java wrapper for the %Qore Type class
 *
 */
package org.qore.lang.reflection;

// java imports
import java.util.HashMap;

// jni module imports
import org.qore.jni.QoreObject;
import org.qore.jni.QoreObjectWrapper;
import org.qore.jni.QoreJavaApi;

//! Java wrapper for the @ref Qore::Reflection::Type class in Qore
/** @note Loads and initializes the Qore library, the jni module, and the reflection module in static initialization
    if necessary

    @deprecated Use @ref jni_dynamic_import_qore_in_java "dynamic imports" instead:
    <tt>import qoremod.reflection.Type;</tt>
*/
@Deprecated
public class Type extends QoreObjectWrapper {
    // static initialization
    static {
        // loads and initializes the Qore library and the jni module (if necessary)
        try {
            QoreJavaApi.initQore();
            QoreJavaApi.callFunction("load_module", "reflection");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    //! creates the object as a wrapper for the Qore object
    public Type(QoreObject obj) throws Throwable {
        super(obj);
    }

    //! Creates the type from the given string
    /** @param typestr the type string

        @throw UNKNOWN-TYPE cannot find the given type
    */
    public Type(String typestr) throws Throwable {
        super(QoreJavaApi.newObjectSave("Reflection::Type", typestr));
    }

    //! returns the type's name
    /** @par Example:
        @code{.java}
    String name = t.getName();
        @endcode

        @return the type's name
    */
    public String getName() throws Throwable {
        return (String)obj.callMethod("getName");
    }

    //! Returns @ref True if the Type object passed as an argument is equal to the current object; @ref False if not
    /** @par Example:
        @code{.java}
    boolean b = t1.isEqual(t2);
        @endcode

        @param type the type to check with the current type for equality

        @return @ref True if the Type object passed as an argument is equal to the current object; @ref False if not
    */
    public boolean isEqual(Type type) throws Throwable {
        return (boolean)obj.callMethod("isEqual", type);
    }

    //! Returns @ref True if the output of the Type object passed as an argument is compatible with the return type if the current Type
    /** @par Example:
        @code{.java}
    boolean b = t1.isOutputCompatible(t2);
        @endcode

        @param type the type to check output value compatibility with the current type

        @return @ref True if the output of the Type object passed as an argument is compatible with the return type if the
        current Type

        @see isCompatible()
    */
    public boolean isOutputCompatible(Type type) throws Throwable {
        return (boolean)obj.callMethod("isOutputCompatible", type);
    }

    //! Returns @ref True if the argument type is compatible with the current type (inputs and outputs)
    /** @par Example:
        @code{.java}
    boolean b = t1.isCompatible(t2);
        @endcode

        @param type the type to check compatibility with the current type

        @return @ref True if the argument type is compatible with the current type (inputs and outputs)

        @note a more strict check than @ref isOutputCompatible()
    */
    public boolean isCompatible(Type type) throws Throwable {
        return (boolean)obj.callMethod("isCompatible", type);
    }

    //! Returns @ref True if the output of the Type object passed as an argument is compatible with the input type if the current Type
    /** @par Example:
        @code{.java}
    int v = t1.isAssignableFrom(t2);
        @endcode

        @param type the type to check output value compatibility with the current type

        @return @ref True if the output of the Type object passed as an argument is compatible with the input type if the
        current Type
    */
    public boolean isAssignableFrom(Type type) throws Throwable {
        return (boolean)obj.callMethod("isAssignableFrom", type);
    }

    //! Returns the value after any conversions by the type
    /** @par Example:
        @code{.java}
    Object v1 = t1.acceptsValue(v0);
        @endcode

        @param value the value to assign to the type

        @return the value to be assigned; can be converted by the type

        @throw RUNTIME-TYPE-ERROR if the value cannot be assigned to the type
    */
    public Object acceptsValue(Object value) throws Throwable {
        return obj.callMethod("acceptsValue", value);
    }

    //! Returns @ref True if the Type object can be assigned from the value given as an argument
    /** @par Example:
        @code{.java}
    boolean b = t.isAssignableFrom(val);
        @endcode

        @param value the value to check

        @return a match code for the assignment; see @ref type_match_constants for possible values

    */
    public int isAssignableFrom(Object value) throws Throwable {
        return ((Long)obj.callMethod("isAssignableFrom", value)).intValue();
    }

    //! Returns the base type code for the type or @ref Qore::NT_ALL for those that don't have types
    /** @par Example:
        @code{.java}
    int t = t.getBaseTypeCode();
        @endcode

        @return the base type code for the type or @ref Qore::NT_ALL for those that don't have types
    */
    public int getBaseTypeCode() throws Throwable {
        return ((Long)obj.callMethod("getBaseTypeCode")).intValue();
    }

    //! Returns a hash of types accepted by this type
    /** @return a hash of types accepted by this type
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Boolean> getAcceptTypeHash() throws Throwable {
        return (HashMap<String, Boolean>)obj.callMethod("getAcceptTypeHash");
    }

    //! Returns a hash of types returned by this type
    /** @return a hash of types returned by this type
    */
    @SuppressWarnings("unchecked")
    public HashMap<String, Boolean> getReturnTypeHash() throws Throwable {
        return (HashMap<String, Boolean>)obj.callMethod("getReturnTypeHash");
    }

    //! Returns @ref True if values of this type can be converted to a scalar value
    /** @par Example:
        @code{.java}
    boolean b = t.canConvertToScalar();
        @endcode

        @return @ref True if the output of the Type object passed as an argument is compatible with the return type if the current Type
    */
    public boolean canConvertToScalar() throws Throwable {
        return (boolean)obj.callMethod("canConvertToScalar");
    }

    //! Returns @ref True if this type has a default value
    /** @par Example:
        @code{.java}
    boolean b = t.canConvertToScalar();
        @endcode

        @return @ref True if this type has a default value
    */
    public boolean hasDefaultValue() throws Throwable {
        return (boolean)obj.callMethod("hasDefaultValue");
    }

    //! Returns the default value for the type or @ref nothing if the type has no default value
    /** @par Example:
        @code{.java}
    Object v = t.getDefaultValue();
        @endcode

        @return the default value for the type or @ref nothing if the type has no default value
    */
    public Object getDefaultValue() throws Throwable {
        return obj.callMethod("getDefaultValue");
    }

    //! Returns \c true if the type accepts and returns @ref nothing in addition to other values
    /** @par Example:
        @code{.java}
    boolean b = type.isOrNothingType();
        @endcode

        @return \c true if the type accepts and returns @ref nothing in addition to other values
    */
    public boolean isOrNothingType() throws Throwable {
        return (boolean)obj.callMethod("isOrNothingType");
    }

    //! Returns the base type for the current type; if the type is already a base type (i.e. not an "or nothing" type), then the same type is returned
    /** @par Example:
        @code{.java}
    Object v = t.getBaseType();
        @endcode

        @return the base type for the current type; if the type is already a base type (i.e. not an "or nothing" type), then the same type is returned

        @see getOrNothingType()
    */
    public Type getBaseType() throws Throwable {
        return new Type((QoreObject)obj.callMethodSave("getBaseType"));
    }

    //! Returns the "or nothing" type for the current type; if the type is already an "or nothing" type (i.e. it already accepts @ref nothing), then the same type is returned
    /** @par Example:
        @code{.java}
    Object v = t.getOrNothingType();
        @endcode

        @return the "or nothing" type for the current type; if the type is already an "or nothing" type (i.e. it already accepts @ref nothing), then the same type is returned

        @see getBaseType()
    */
    public Type getOrNothingType() throws Throwable {
        return new Type((QoreObject)obj.callMethodSave("getOrNothingType"));
    }

    //! Returns the element type for complex list and hash types, if any, otherwise returns @ref nothing
    /** @par Example:
        @code{.java}
    *Type element_type = type.getElementType();
        @endcode

        @return the element type for complex list and hash types, if any, otherwise returns @ref nothing
    */
    public Type getElementType() throws Throwable {
        QoreObject rv = (QoreObject)obj.callMethodSave("getElementType");
        if (rv != null) {
            return new Type(rv);
        }
        return null;
    }

    //! Returns \c true if the type is a TypedHash type, \c false if not
    /** @par Example:
        @code{.java}
    boolean b = type.isTypedHash();
        @endcode

        @return \c true if the type is a TypedHash type, \c false if not
    */
    public boolean isTypedHash() throws Throwable {
        return (boolean)obj.callMethod("isTypedHash");
    }

    //! Returns \c true if the type is not a wildcard type; i.e. has type restrictions
    /** @par Example:
        @code{.java}
    boolean b = type.hasType();
        @endcode

        @return \c true if the type is not a wildcard type; i.e. has type restrictions
    */
    public boolean hasType() throws Throwable {
        return (boolean)obj.callMethod("hasType");
    }

    //! Returns the type object for the value
    /** @par Example:
        @code{.java}
    Type t = getType(v);
        @endcode

        @param v the value to return the type object for

        @return the type object for the value
    */
    public static Type getType(Object v) throws Throwable {
        return new Type((QoreObject)QoreJavaApi.callStaticMethodSave("Type", "getType", v));
    }

    //! Returns the "or nothing" type object for the value
    /** @par Example:
        @code{.java}
    Type t = Type::getOrNothingType(v);
        @endcode

        @return the "or nothing" type object for the value
    */
    public static Type getOrNothingType(Object v) throws Throwable {
        return new Type((QoreObject)QoreJavaApi.callStaticMethodSave("Type", "getOrNothingType", v));
    }
}

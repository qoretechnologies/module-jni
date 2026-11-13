# Java to Qore type mapping

Copyright (C) 2026 Qore Technologies, s.r.o.

The `jni` module declares a Qore type for every Java parameter, return value,
field, static field and constant of the Qore classes it creates for Java
classes. This document describes how those types are derived, the rule they
follow, and where Qore checks them.

The single source of truth is `QoreJniClassMap::getQoreType()`
(`src/QoreJniClassMap.cpp`) with the `jtmap` table. Every use goes through it:
`BaseMethod::getParamTypes()` and `BaseMethod::getReturnTypeInfo()`
(`src/Method.cpp`) and `BaseField::getQoreTypeInfo()` (`src/Field.h`), which
`QoreJniClassMap::doFields()` uses for fields, static fields and constants.
No caller adjusts the type it gets.

## Uses

`QoreJniClassMap::JavaTypeUse` has four values:

- `Param`: a parameter; a Qore value is converted to the Java type
  (`QoreToJava::toObject()`).
- `VarargsParam`: the variable-arguments array of an abstract varargs method
  (see Parameters).
- `VarargsArrayParam`: the variable-arguments array of a varargs method that
  can be called, given as an array (see Parameters).
- `Value`: a value converted to Qore (`JavaToQore::convertToQore()`): a return
  value, a field, a static field or a constant.

## The nullability rule

A Java reference (an object, including a `String`, a boxed value such as an
`Integer`, or an array) can be `null`, which is converted to `NOTHING`.
Therefore:

> The declared type of a Java reference type always accepts `NOTHING`,
> without converting it, in every use: parameters, return values, fields,
> static fields and constants. Only Java primitives (`int`, `boolean`,
> `char`, ...) have types that do not.

`getQoreType()` applies the rule last for every non-primitive class
(`qore_get_or_nothing_type()`), so no mapping can bypass it; the
variable-arguments array of a varargs method is the one exception (see
Parameters). A soft list
(`softlist<T>`), which is what a Java array maps to, converts `NOTHING` to an
empty list; the rule makes it `*softlist<T>`, so a `null` array is `NOTHING`.

The rule was not applied before module version 3.0.0: `String` mapped to
`string` and the boxed types to `int`, `float` and `bool` in every use. When
module-jni a82ce95 made field reads go through typed accessors (Qore
68b691e5c4 checks the value read against the member's type), a `null` `String`
field raised `RUNTIME-TYPE-ERROR: member 'prefix' expects type 'string', but
got no value`, and a `null` array field read as an empty list.

The declared type of a value also accepts every value the conversion yields:

| Java class | Declared type of a value |
|---|---|
| `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double` | `bool`, `int`, `int`, `int`, `int`, `int`, `float`, `float` |
| `String` | `*string` |
| `Boolean` | `*bool` |
| `Byte`, `Short`, `Integer`, `Long`, `Character` | `*int` |
| `Float`, `Double` | `*float` |
| `BigDecimal` | `*number` |
| `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `LocalDateTime`, `LocalDate`, `LocalTime`, `Instant`, `java.sql.Timestamp`, `java.sql.Date`, `java.sql.Time`, `QoreRelativeTime` | `*date` |
| `Object` | `auto` |
| a class that the classes above implement or extend: `Number`, `CharSequence`, `Comparable`, `Temporal`, `java.util.Date`, ... | `auto` (`jni_may_be_converted_value()`) |
| a class that lists or maps implement or extend: `Collection`, `Iterable`, `Serializable`, ... | `auto` |
| a `List` | `union<*list<auto>, *object<L>>` (the Java object when compatibility types are in effect) |
| a `Map` with `String` keys | `union<*hash<auto>, *object<M>>` |
| any other `Map` | `auto` |
| `byte[]` | `union<*binary, *list<auto>>` (a list of integers when compatibility types are in effect) |
| `T[]` | `*softlist<E>`, where `E` is the declared type of a `T` value (`int` for `int[]`, `*string` for `String[]`) |
| any other class `C` | `*object<C>` |
| any type of a `private` member | `auto` |

A `static final` field is a constant. A constant holding a value has the type of
its value. A `null` constant has no value and is declared with the field's type.

## Parameters

Parameters follow the nullability rule as well: a parameter of a reference
type takes `NOTHING`, which reaches Java as `null` (`*string`, `*int`,
`*softlist<E>`, `*object<C>`, ...), and a primitive parameter does not. A
parameter of a class mapped in `jtmap` also has an alternative variant taking
the Java object (ex: `*object<String>`), except `Character` (see below).

The one exception is the variable-arguments array of a Java varargs method,
because Java passes an empty array when there are no variable arguments (ex:
`cls.getMethod("f")`, `Method.invoke(obj)`). A varargs method or constructor
that can be called has two variants:

- the array (`JavaTypeUse::VarargsArrayParam`): `*list<E>` (`*binary` for
  `byte...`), which does not convert a single value to a list; `NOTHING` or a
  missing argument is an empty array (`BaseMethod::getVarargsArray()`)
- the variable arguments given as separate arguments
  (`jni_add_loose_varargs_variants()` in `QoreJniClassMap.cpp`): the fixed
  parameters, then the first variable argument declared with the element type
  `E` (`QoreJniClassMap::getVarargsElementType()`), then extra arguments
  (`QCF_USES_EXTRA_ARGS`), which Qore passes as they are given and
  `BaseMethod::getVarargsArray()` packs into the array. If another variant has
  the same parameter types (a Java method with fixed parameters for that
  number of arguments, ex: `EnumSet.of(E, E)`), the variant declares one more
  element-typed argument, until none has, so the fixed-arity method is called
  for its number of arguments, as in Java.

As in Java, a single argument that is the array itself is passed as the array:
a list, a binary for `byte[]`, or a Java array object that is an instance of
the array's class; any other arguments are packed into the array. When the
element type is not `auto`, a third variant takes `object<JavaArray>` for the
array, which neither the list nor the element type accepts; the `JavaArray`
class is created before any Java class for this
(`preinitJavaArrayClass()`).

The variants added for collisions are found in the class and its parents
(`jni_get_parent_variants()`, public variants without extra arguments only):
a parent's abstract variant with the same types (`I1.m(String)` for
`I2.m(String...)`) is a collision, so the concrete variant for separate
arguments does not implement it; a parent's concrete variant is a collision
only if it is a public Java method with fixed parameters, which is added to
the child calling the inherited Java method
(`QoreJniClassMap::addInheritedFixedArityVariant()`, no bridge methods, the
choice among several by `jni_choose_variant()`), as Qore calls a variant of
the child before one of its parents. The array variants of a parent's varargs
method (`*list<E>`, `object<JavaArray>`) are not collisions.

A varargs method of a class that is not abstract has
`QCF_ABSTRACT_OVERRIDE_ALL` on its array variant: Java requires the class to
implement every abstract method it inherits, and the inherited abstract
variant declares the array as a soft list, so the signatures differ. A class
with a Qore parent (`jni_has_qore_parent()`) does not get the flag, as Java
does not know the abstract variants of the Qore parent.

An abstract varargs method (`JavaTypeUse::VarargsParam`) keeps `softlist<E>`,
which a Qore class implementing it declares, and gets the variant for
separate arguments as a concrete variant calling the Java method virtually.
`QoreJavaParamHelper::findOverriddenMethod()` accepts `*softlist<E>`,
`softlist<E>` and `*list<E>` for the array of a varargs Java method that a
Qore method overrides.

The array must not be a soft list for a method that can be called: a soft
list converts a single value to a list, so the first of several separate
variable arguments became a list nested in the array (`[[a], b]`), and with
`%strict-args` the further arguments matched no variant.

**The overload rule: a Qore argument selects the variant Java would select for
the converted value.** `QoreToJava::toObject()` converts the following Qore
values for a parameter of any class that the converted value's class
implements or extends, as it does for `Object`:

| Qore value | Java value |
|---|---|
| string | `String` |
| int | `Long` |
| float | `Double` |
| bool | `Boolean` |
| number | `BigDecimal` |
| date | `ZonedDateTime` |
| binary | `byte[]` |
| list | `ArrayList` |
| hash | `org.qore.jni.Hash` |

The declared type of such a parameter is a union of those Qore types and the
object type (`jni_get_converted_param_type()`): for example,
`union<*string, *object<CharSequence>>` for a `CharSequence`, and
`union<*string, *int, *float, *bool, *number, *date, *binary, *list<auto>,
*hash<auto>, *object<Serializable>>` for a `Serializable`. The union ranks
above the `auto` of an `Object` parameter, as Java prefers the more specific
method. So `m(Object)`/`m(CharSequence)` with a string calls
`m(CharSequence)`, as Java does for a `String`.

An integer is not passed for a `Character` parameter, because Java does not
convert an `int` to a `Character`: `m(Character)`/`m(Object)` with an integer
calls `m(Object)`, and a lone `m(Character)` rejects an integer, as in Java.

For overloads that all accept `NOTHING`, the first variant added is called
(Qore issue #1897 rules, Qore a68e534ee5). `jni_get_variants()` adds them
deepest class first, then by signature, so `m(String)` comes before
`m(String[])` and `m(Object)`, as Java's most specific method does for a
`null` argument where Java is not ambiguous.

A Qore integer matches every Java integer class (`int`, `long`, `Integer`,
`Long`, ...), so between `m(Integer)` and `m(Number)` an integer selects
`m(Integer)`, while Java, with a `Long` argument, selects `m(Number)`. This is
a known difference that predates this mapping.

`test/jni.qtest` "parameter overload choices test" compares the variant Qore
selects for each Qore argument with the variant Java selects for the
converted value (`OverloadChoices.javaChoices()`).

## Where types are checked

| Use | Parse time | Run time |
|---|---|---|
| Parameter | variant matching and argument types | variant matching; the argument is converted by `QoreToJava` |
| Return value | the expression's type (narrowing, lvalue checks) | not checked; the converted value is returned |
| Field (instance) | assignment type | the value read is checked against the member type (`evalMemberAccessorGet()`), and assignments are checked |
| Static field | assignment type | the value read is checked (`QoreVarInfo::getAccessorValue()`), and assignments are checked |
| Constant | the constant's type | not checked |

A return value or constant type that does not accept a value the conversion
yields does not fail at run time. It causes wrong parse-time narrowing or a
`PARSE-TYPE-ERROR` in code that uses the value as the type it has (ex:
`string s = obj.getCharSequence()`).

## Tests

- `test/jni.qtest` "value type mapping guard test" enumerates the reference
  types of `ValueTypeMapping` as parameters, return values, fields, static
  fields and constants. It fails if any of them has a type that does not
  accept `NOTHING` without converting it, or if a primitive has one that does,
  and checks the varargs exception.
- `test/jni-varargs.qtest` checks what Java receives for the variable
  arguments of varargs methods given as separate arguments, as a list, as a
  Java array or not at all, with and without strict arguments.
- `test/jni.qtest` "parameter overload choices test" checks that Qore
  arguments select the variants Java selects.
- `test/jni.qtest` "converted return values test", "nullable field types test",
  "nullable instance fields test", "nullable static fields test", "converted
  value fields test" and "final static fields test".
- `test/jni-collection-types.qtest` covers the declared types of lists and maps.

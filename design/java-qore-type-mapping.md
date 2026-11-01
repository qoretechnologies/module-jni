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

`QoreJniClassMap::JavaTypeUse` has two values:

- `Param`: a parameter; a Qore value is converted to the Java type
  (`QoreToJava::toObject()`).
- `Value`: a value converted to Qore (`JavaToQore::convertToQore()`): a return
  value, a field, a static field or a constant.

## The nullability rule

A Java reference (an object, including a `String`, a boxed value such as an
`Integer`, or an array) can be `null`, which is converted to `NOTHING`.
Therefore:

> The declared type of a value of a Java reference type always accepts
> `NOTHING`, without converting it. Only Java primitives (`int`, `boolean`,
> `char`, ...) have types that do not.

`getQoreType()` applies the rule last for every `Value` of a non-primitive
class (`qore_get_or_nothing_type()`), so no mapping can bypass it. A soft list
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

## Parameters: an open decision

Parameter types are unchanged. Here `String` and the boxed types map to types
that do not accept `NOTHING` (`string`, `int`, ...), and an array maps to a
`softlist<E>`. A `null` reaches Java through the alternative variant that takes
the Java object (`*object<String>`), which every parameter mapped in `jtmap`
has. `Character` and the converted supertypes are declared with their class
type.

Applying the rule to parameters conflicts with two existing behaviours:

1. **Overloads with `NOTHING`.** For `m(String)` and `m(String[])`, a call
   passing `NOTHING` selects `m(String)` with `null` today, whether the argument
   is the literal `NOTHING` or a variable holding it. With `*string` and
   `softlist<*string>`, a literal `NOTHING` selects `m(String[])` with an empty
   array at parse time, and a variable holding `NOTHING` selects `m(String)` at
   run time. With `*string` and `*softlist<*string>`, the literal selects
   `m(String[])` with `null`, and the variable selects `m(String)`. This is what
   the 2019 comment in `jtmap` ("because of automatic array conversions",
   67a1c32) protected.
2. **Overrides.** A Qore class that implements a Java method with the current
   parameter types (ex: `messageArrived(string topic, ...)` in jni.qtest's
   codegen test, or `QoreInvocationHandler::invoke(auto, *Method, softlist<auto>)`
   in `qlib/MqttDataProvider`) no longer implements the abstract variant
   (`ABSTRACT-CLASS-ERROR`).

Until the decision is made, the exception is limited to `JavaTypeUse::Param`
and to the `param_type` column of `jtmap`.

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
  types of `ValueTypeMapping` as return values, fields, static fields and
  constants. It fails if any of them has a type that does not accept `NOTHING`
  without converting it, or if a primitive has one that does.
- `test/jni.qtest` "converted return values test", "nullable field types test",
  "nullable instance fields test", "nullable static fields test", "converted
  value fields test" and "final static fields test".
- `test/jni-collection-types.qtest` covers the declared types of lists and maps.

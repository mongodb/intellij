# MQL BSON Type
-----------

## Abstract

This specification documents the different kinds of BSON types and how they are related to the
original source code of an [MQL Query](../mql-query/mql-query.md). This document aims to provide
information about the behaviour of dialects and linters on the computation of the original
expression BSON type.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

[BSON](https://bsonspec.org/spec.html) is a binary format that is used to communicate between the
MongoDB Client (through a driver) and a MongoDB Cluster. MQL BSON (from now on we will just say BSON) 
is a superset of the original BSON types. For example some semantics, like BsonAnyOf, are not part
of the original BSON.

A BSON Type represents the data type inferred from the original source code or from a MongoDB sample
of documents. A BSON Type MUST be consumable by a MongoDB Cluster and its serialization MUST be
BSON 1.1 compliant.

### Primitive BSON Types

#### BsonString

A BsonString is a sequence of Unicode characters. 

#### BsonBoolean

A BsonBoolean represents a disjoint true or false values. The actual internal encoding is left to the
original BSON 1.1 specification.

#### BsonDate

A BsonDate represents a date and a time, serializable to a UNIX timestamp. This specific type MAY be 
represented differently in some dialects.

In any Java-based dialects, a BsonDate can be represented as:

* [java.util.Date](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/util/Date.html)
* [java.time.Instant](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/Instant.html)
* [java.time.LocalDate](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/LocalDate.html)
* [java.time.LocalDateTime](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/LocalDateTime.html)

#### BsonObjectId

A BsonObjectId represents a 12 bytes unique identifier for an object. 

#### BsonInt32

A signed integer of 32 bits precision. In Java it's mapped to an `int` type.

#### BsonInt64

A signed integer of 64 bits precision. In Java it's mapped to both `long` and `BigInteger`.

#### BsonDouble

A 64bit floating point number. In Java it's mapped to both float and double.

#### BsonDecimal128

A 128bit floating point number. In Java it's mapped to BigDecimal.

#### BsonNull

Represents the absence of a value. 

#### BsonAny

Represents any possible type. Essentially, every type is a subtype of BsonAny.

#### BsonAnyOf

Represents an union of types. For example, BsonAnyOf([BsonString, BsonInt32]). 

#### BsonObject

Represents the shape of a BSON document.

#### BsonArray

Represents a list of elements of a single type. For example: [ 1, 2, 3 ] is a BsonArray.

#### BsonUUID

Represents a UUID. BSON does not have a type for UUIDs, it uses a subtype of the Binary type, however,
from a source code perspective, it's convenient to have UUID as with its own identity due to being
a standard available in most languages.

#### BsonEnum

Represents a closed set of strings. They are similar to a Java enum. BSON does not have a type for
enums, however, due to their properties, it's convenient to have a type for them. The most relevant
aspect of BsonEnums is that they have a fixed cardinality, so they can be extremely efficient for
prefix compression.

#### ComputedBsonType

A ComputedBsonType is a type that represents an expression that happens outside the boundaries
of the user. The typical use case is for expressions defined as MQL expressions (like $expr) that
will run on a valid MongoDB Cluster.

They contain a `baseType` that is the inferred type of the result of computing the expression. In
case the `baseType` can not be inferred, it MUST be BsonAny.

### Type Assignability

Assignable types MUST not change the semantics of a query when they are swapped. Let's say that
we have a query $Q$, and two variants, $Q_A$ and $Q_B$, where $Q_A$ and $Q_B$ differ on the specified type
in either a field or a value reference.

We will say that type $A$ is assignable to type $B$ if $Q_A$ and $Q_B$ are 
[equivalent queries](/main/packages/mongodb-mql-model/src/docs/md/mql-query/mql-query.md#query-equivalence).

The most common usage of this property is type checking, to ensure that given a type in a query, it
does behave properly based on the type of the schema of a field.

Type assignability MAY NOT be commutative.

#### Assignability table

| ⬇️ can be assigned to ➡️ | BsonString | BsonBoolean | BsonDate | BsonObjectId | BsonInt32 | BsonInt64 | BsonDouble | BsonDecimal128 | BsonNull | BsonAny | BsonAnyOf | BsonObject | BsonArray | ComputedBsonType | BsonUUID | BsonEnum |
|--------------------------|:----------:|:-----------:|:--------:|:------------:|:---------:|:---------:|:----------:|:--------------:|:--------:|:-------:|:---------:|:----------:|:---------:|:-----------------|----------|----------|
| BsonString               |     🟢     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonBoolean              |     🔴     |     🟢      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonDate                 |     🔴     |     🔴      |    🟢    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonObjectId             |     🔴     |     🔴      |    🔴    |      🟢      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonInt32                |     🔴     |     🔴      |    🔴    |      🔴      |    🟢     |    🟢     |     🟢     |       🟢       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonInt64                |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🟢     |     🔴     |       🟢       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonDouble               |     🔴     |     🔴      |    🔴    |      🔴      |  🟠$^2$   |  🟠$^2$   |     🟢     |       🟢       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonDecimal128           |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🟢       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonNull                 |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🟢    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonAny                  |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonAnyOf                |   🟠$^1$   |   🟠$^1$    |  🟠$^1$  |    🟠$^1$    |  🟠$^1$   |  🟠$^1$   |   🟠$^1$   |     🟠$^1$     |  🟠$^1$  |   🟢    |  🟠$^1$   |   🟠$^1$   |  🟠$^4$   | 🟠$^6$           | 🟠$^1$   | 🔴       |
| BsonObject               |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |   🟠$^3$   |  🟠$^4$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonArray                |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |  🟠$^5$   | 🟠$^6$           | 🔴       | 🔴       |
| ComputedBsonType         |   🟠$^6$   |   🟠$^6$    |  🟠$^6$  |    🟠$^6$    |  🟠$^6$   |  🟠$^6$   |   🟠$^6$   |     🟠$^6$     |  🟠$^6$  | 🟠$^6$  |  🟠$^6$   |   🟠$^6$   |  🟠$^6$   | 🟠$^6$           | 🔴       | 🔴       |
| BsonUUID                 |     🔴     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |    🔴     | 🔴               | 🟢       | 🔴       |
| BsonEnum                 |     🟢     |     🔴      |    🔴    |      🔴      |    🔴     |    🔴     |     🔴     |       🔴       |    🔴    |   🟢    |  🟠$^1$   |     🔴     |    🔴     | 🔴               | 🟢       | 🟠$^7$   |

* 🟠$^1$: $A$ is assignable to $BsonAnyOf(B)$ only if $A$ is assignable to $B$.
* 🟠$^2$: It's assignable but there might be a significant loss of precision.
* 🟠$^3$: $BsonObject A$ is assignable to $B$ if $A$ is a subset of $B$.
* 🟠$^4$: $A$ is assignable to $BsonArray(B)$ only if $A$ is assignable to $B$.
* 🟠$^5$: $BsonArray(A)$ is assignable to $BsonArray(B)$ only if $A$ is assignable to $B$.
* 🟠$^6$: $A$ is assignable to $ComputedBsonType(BaseType)$ only if $A$ is assignable to $BaseType$.
* 🟠$^7$: $A$ is assignable to $B$ only if $A.members$ is a subset of $B.members$

### Type mapping

#### Java

| Java Type     | Bson Type                           |
|:--------------|:------------------------------------|
| null          | BsonNull                            |
| float         | BsonDouble                          |
| Float         | BsonAnyOf(BsonNull, BsonDouble)     |
| double        | BsonDouble                          |
| Double        | BsonAnyOf(BsonNull, BsonDouble)     |
| BigDecimal    | BsonAnyOf(BsonNull, BsonDecimal128) |
| boolean       | BsonBoolean                         |
| short         | BsonInt32                           |
| Short         | BsonAnyOf(BsonNull, BsonInt32)      |
| int           | BsonInt32                           |
| Integer       | BsonAnyOf(BsonNull, BsonInt32)      |
| BigInteger    | BsonAnyOf(BsonNull, BsonInt64)      |
| long          | BsonInt64                           |
| Long          | BsonAnyOf(BsonNull, BsonInt64)      |
| CharSequence  | BsonAnyOf(BsonNull, BsonString)     |
| String        | BsonAnyOf(BsonNull, BsonString)     |
| Date          | BsonAnyOf(BsonNull, BsonDate)       |
| Instant       | BsonAnyOf(BsonNull, BsonDate)       |
| LocalDate     | BsonAnyOf(BsonNull, BsonDate)       |
| LocalDateTime | BsonAnyOf(BsonNull, BsonDate)       |
| Collection<T> | BsonAnyOf(BsonNull, BsonArray(T))   |
| Map<K, V>     | BsonAnyOf(BsonNull, BsonObject)     |
| Object        | BsonAnyOf(BsonNull, BsonObject)     |

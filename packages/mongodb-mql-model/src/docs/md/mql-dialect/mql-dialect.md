# MQL Component
---------------

## Abstract

This specification documents the architecture of an MQL Dialect and its goal in the whole system and
how does it interact with other parts of the MQL model. It is aimed mainly to developers implementing
support for new languages and frameworks.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

MQL Dialects (from now on just dialects) represent a way to define MQL queries by some mean of source
code definition. Dialects are usually a pair of a language and a framework. For example, the Java Driver
dialect would parse queries written in Java code using the official MongoDB driver.

Dialects MUST generate valid queries for any target cluster if the query is valid in the source code. However,
the MAY generate partial queries that can not be run directly.

A dialect MAY have a parser, a formatter and a connection context extractor. A dialect also depends 
on the source and the context of the dialect itself.

### Source

The Source is the token that represents part of an MQL Query in the dialect. In the Java Driver, as it
depends on IntelliJs PSI, the source would be a PsiElement. Sources, however 
MAY be optional and not provided by the dialect.

### Context

The Context is an opaque data structure that represents additional data, not specified in the Source,
that is relevant to understand a query. For example, in Spring Data, the namespace of the query is 
not provided in the query as is. The database is defined in a configuration file, and the collection
is specified in a Java class as an annotation.

### Parser

The parser is the actual implementation that will read code structured in the Source type. For example,
for the Java Driver, it parses code as PsiElements. For the signature of the parser, take a look at
the DialectParser interface in the [Dialect.kt](/packages/mongodb-dialects/src/main/kotlin/com/mongodb/jbplugin/dialects/Dialect.kt) 
file.

### Formatter

The formatter is the component that, given a query in MQL model, knows how to generate Source tokens
that are specific to this dialect.

### ConnectionContextExtractor

The extractor is the component that, given the context of where the query is written, extracts additional
metadata relevant for the query. For example, for Spring Data, it retrieves the configuration file from
the IntelliJ Project.

# MQL Component
---------------

## Abstract

This specification documents what MQL Engines are and how they interact with the overall MQL Model
and the system.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

MQL Engines (for now on, engines) are modules that provide functionality that only depends on the
MQL Model. Their main purpose it to provide insights based on the query model to users of the engine.

Engines MUST be independent of the origin of the query: for them it MUST NOT matter if the query
is a Java query or a shell query. All engines are implement in the mongodb-mql-engines package.

Currently, there are three engines.

### Autocomplete

Autocomplete calculates the list of suggestion of possible fields given a query.

### Indexing

Suggests, based on existing queries, what are the best indexes to fulfill them.

### Linting

Analyses and warns about defects on the code. There are three linters currently:

#### FieldChecking

Detects whether the specified field in the query exists in the target collection or if the field
type provided is invalid.

#### IndexChecking

Detects whether the specified query can be covered by an index or not.

### NamespaceChecking

Detects whether the target namespace exists in the cluster.

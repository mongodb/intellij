# Query Inspections
--------------------

## Abstract

This document describes the different mechanisms that can be used to analyse relevant properties from
written queries. 

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY",
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

A Query Inspection is a process that analyses a query and MAY provide insights on different
categories of relevant information about the processed query. The provided query MAY be valid and
supported by any target cluster.

An inspection MAY have additional requirements on the validity of the query to provide insights.

Inspections MUST be part of one of the following categories:

* Performance: insights related to query efficiency on a target cluster.
* Correctness: insights related to potentially invalid usage of operators and values in a query.
* Environment: insights related to potentially incorrect configuration of the inspection engine.
* Other: insights not related to any of the other previous categories.

## Inspections

### Performance

#### QueryNotUsingIndex

Provides insights whether a query is not using an index. Any query that will run with a COLLSCAN
will trigger this inspection.

#### QueryNotUsingIndexEffectively

Provides insights whether a query is using an index but MAY perform poorly in the target cluster
under considerably load.

### Correctness

#### FieldDoesNotExist

Provides insights when a query references a field that does not exist in the target collection's schema.

#### TypeMismatch

Provides insights when a query attempts to compare or operate on fields with incompatible types in the target collection's schema.

### Environment

#### DatabaseDoesNotExist

Provides insights when a query references a database that does not exist in the target cluster.

#### CollectionDoesNotExist

Provides insights when a query references a collection that does not exist in the specified database.

#### NoDatabaseInferred

Provides insights when a query specifies a collection without a database context.

#### NoCollectionSpecified

Provides insights when a query does not specify a target collection.

# Index Analyzer
---------------

## Abstract

This document describes the algorithm used to suggest indexes based on existing queries. 
It serves as a reference for users seeking optimal results from the algorithm or wishing to understand its internal mechanics.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", 
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

The algorithm MUST process a query and its sibling queries as follows. The current
algorithm is based on the [ESR guidelines](https://www.mongodb.com/docs/manual/tutorial/equality-sort-range-rule/).

### Step 1: Extract Field Usages

For each query:
* The algorithm MUST extract all fields used in filtering and sorting.
* The algorithm MUST retrieve the operation type, the field name, and whether the value is constant or runtime-dependent.

### Step 2: Categorize Operations

Each field usage MUST be classified into one of the following roles:

* **Equality** (`equality`): Exact match conditions (e.g., `field = value`).
* **Sorting** (`sort`): Fields used for ordering results.
* **Range** (`range`): Conditions imposing a range constraint (e.g., `field > value` or `field < value`).

### Step 3: Group and Prioritize Field Usages

The algorithm MUST group all usages of the same field under a common key and sort them by their role with the following priority:

1. **Equality**
2. **Sort**
3. **Range**

### Step 4: Determine the Least Specific Usage

For each field, the algorithm MUST select the *least specific usage*. The least specific usage is 
defined as the usage containing the least amount of determinable information at the moment of 
defining the index for the query.

* The selection criteria **MUST** prioritize the safest option. For example:
    - If both a constant value and a runtime value are used in a query, the runtime value **MUST** be selected.

### Step 5: Rank Fields for Indexing

The algorithm **SHOULD** prioritize fields for index creation based on:

1. **Role priority**:
    - `equality` fields first
    - Followed by `sort` fields
    - Lastly, `range` fields
2. **Cardinality**:
    - Fields with lower cardinality **SHOULD** be prioritized to enhance prefix compression.

### Step 6: Index Consolidation

Given all the defined indexes for the query and its siblings, the algorithm **MUST** consolidate 
them into the most effective index for use across sibling queries and the main query.

1. **Partition queries** into groups where indexes share a common prefix.
2. **Assign the main query’s index** to the appropriate partition.
3. **Select the most comprehensive index** from the partition:
    - Choose the index covering the greatest number of fields.

## Glossary

* **SiblingQueriesFinder**: A component that identifies and returns queries that share the same namespace as an existing query.
* **Operation**: The type of comparison applied to a field, which can be `equality`, `sorting`, or `range`.
* **Usage**: An instance of a field, an operator, and a value in a given query.
* **Cardinality**: The number of distinct values a specific [BSON type](/packages/mongodb-mql-model/src/docs/md/bson-type/bson-type.md) can take.
* **Redundant Indexes**: An index $A$ is redundant if there exists an index $B$ where $A$ is a prefix of $B$.
    - For example, given the indexes:
        - $A = \{ a: 1 \}$
        - $B = \{ a: 1, b: 1 \}$
    - $A$ is redundant because $B$ contains it as a prefix.

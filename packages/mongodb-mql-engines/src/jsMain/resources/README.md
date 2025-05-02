# mongodb-mql-engines

Exposes, on demand, engines used by the MongoDB IntelliJ Plugin for
index suggestions.

## Example Usage:

```js
const mql = require('mongodb-mql-engines');

const sampleDocuments = [ { a: 1, b: 2 }, { a: 5, b: true } ]

const ns = mql.analyzeNamespace({ database: "mydb", collection: "mycoll" }, sampleDocuments)
const query = mql.parseQuery({ a: 1, b: 2 }, ns)
const index = await mql.suggestIndex([ query ])

console.log(index);
```

Expected output:

```js
{ index: { a: 1, b: 1 }, coveredQueries: [ { a: 1, b: 2 } ] }
```

## API

### analyzeNamespace(namespace: { database: String, collection: String }, sampleDocuments?: Array<Json>): Namespace

Creates a new namespace reference with the data distribution calculated from the sample documents. If no sample
documents are provided, it won't use this information for suggesting indexes or running any other engine.

The more documents are provided as a sample, the better the index suggestion will be.

### mql.parseQuery(query: Json, namespace: Namespace): Query

Parses a JSON query, defined as plain JSON or relaxed EJSON and attaches it to the provided namespace.

### mql.suggestIndex(queries: Array<Query>): SuggestedIndex

Analyses all the provided queries and returns the best index to cover all of them. In case only
a single query is analysed, provide an array with that single query.

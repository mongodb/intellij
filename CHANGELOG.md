# Changelog

MongoDB plugin for IntelliJ IDEA.

## Unreleased

### Added
* [INTELLIJ-223](https://jira.mongodb.org/browse/INTELLIJ-223) Support for partial indexes on queries that depend on constant values.
* [INTELLIJ-226](https://jira.mongodb.org/browse/INTELLIJ-226) Consider sort direction specified in the user code when suggesting indexes.
* [INTELLIJ-232](https://jira.mongodb.org/browse/INTELLIJ-219) Consider selectivity of a field over cardinality when suggesting indexes.
* [INTELLIJ-219](https://jira.mongodb.org/browse/INTELLIJ-219) Implement index consolidation of a query and it's siblings.
* [INTELLIJ-222](https://jira.mongodb.org/browse/INTELLIJ-222) Implement the ESR guideline for index suggestions.
* [INTELLIJ-221](https://jira.mongodb.org/browse/INTELLIJ-221) Add a new setting for a soft maximum number of indexes per collection for index suggestions.
* [INTELLIJ-230](https://jira.mongodb.org/browse/INTELLIJ-230) Officially introduce support for Reactive Spring Data. Syntax was similar, so it was already partially working, now we have a fully-fledged test suite to ensure we don't break it.
* [INTELLIJ-211](https://jira.mongodb.org/browse/INTELLIJ-211) Introduce a new configurable plugin setting `sampleSize` which dictates how many documents to be fetched and used for analysing a collection's schema.
* [INTELLIJ-199](https://jira.mongodb.org/browse/INTELLIJ-199) Add support for parsing, inspecting and autocompleting in method call used for chaining a `Sort` object on top of a Criteria chain in Spring data MongoDB using `Query.with()` call.
* [INTELLIJ-197](https://jira.mongodb.org/browse/INTELLIJ-197) Add support for generating shell syntax for $group stage and supported accumulators when running queries.
* [INTELLIJ-198](https://jira.mongodb.org/browse/INTELLIJ-198) New modal to provide default values when generating queries with unknown runtime expressions.
* [INTELLIJ-175](https://jira.mongodb.org/browse/INTELLIJ-175) Add support for parsing, inspecting and autocompleting in a group stage written using `Aggregation.group` and chained `GroupOperation`s using `sum`, `avg`, `first`, `last`, `max`, `min`, `push` and `addToSet`.
* [INTELLIJ-196](https://jira.mongodb.org/browse/INTELLIJ-196) Add support for $sort when generating the query into DataGrip.
* [INTELLIJ-195](https://jira.mongodb.org/browse/INTELLIJ-195) Add support for $unwind when generating the query into DataGrip.
* [INTELLIJ-194](https://jira.mongodb.org/browse/INTELLIJ-194) Add support for $addFields when generating the query into DataGrip.
* [INTELLIJ-193](https://jira.mongodb.org/browse/INTELLIJ-193) Add support for generating aggregates with $match and $project.
* [INTELLIJ-189](https://jira.mongodb.org/browse/INTELLIJ-189) Add support for generating update queries.
* [INTELLIJ-177](https://jira.mongodb.org/browse/INTELLIJ-177) Add support for parsing, inspecting and autocompleting in a addFields stage written using `Aggregation.addFields` and chained `AddFieldsOperation`s using `addFieldWithValue`, `addFieldWithValueOf`, `addField().withValue()` and `addField().withValueOf()`. Parsing boxed Java values is not supported yet.
* [INTELLIJ-174](https://jira.mongodb.org/browse/INTELLIJ-174) Add support for parsing, inspecting and autocompleting in a sort stage written using `Aggregation.sort` and chained `SortOperation`s using `and`. All the overloads of creating a `Sort` object are supported.
* [INTELLIJ-188](https://jira.mongodb.org/browse/INTELLIJ-188) Support for generating sort in the query generator.
* [INTELLIJ-186](https://jira.mongodb.org/browse/INTELLIJ-186) Support for parsing Sorts in the Java Driver.
* [INTELLIJ-187](https://jira.mongodb.org/browse/INTELLIJ-187) Use safe execution plans by default. Allow full execution
  plans through a Plugin settings flag.
* [INTELLIJ-180](https://jira.mongodb.org/browse/INTELLIJ-180) Telemetry when inspections are shown and resolved.
  It can be disabled in the Plugin settings.
* [INTELLIJ-176](https://jira.mongodb.org/browse/INTELLIJ-176) Add support for parsing, inspecting and autocompleting in an unwind stage written using `Aggregation.unwind`.
* [INTELLIJ-173](https://jira.mongodb.org/browse/INTELLIJ-173) Add support for parsing, inspecting and autocompleting in a project stage written using `Aggregation.match` and chained `ProjectionOperations` using `andInclude` and `andExclude`.
* [INTELLIJ-172](https://jira.mongodb.org/browse/INTELLIJ-172) Add support for parsing, inspecting and autocompleting in an aggregation written using Spring Data MongoDB (`MongoTemplate.aggregate`, `MongoTemplate.aggregateStream`) and a match stage written using `Aggregation.match`.
* [INTELLIJ-179](https://jira.mongodb.org/browse/INTELLIJ-179) Telemetry when Create Index intention is clicked.
  It can be disabled in the Plugin settings.
* [INTELLIJ-178](https://jira.mongodb.org/browse/INTELLIJ-178) Telemetry when the Run Query button is clicked.
  It can be disabled in the Plugin settings.
* [INTELLIJ-153](https://jira.mongodb.org/browse/INTELLIJ-153) Add support for parsing, linting and
  autocompleting fields in Accumulators.topN and Accumulators.bottomN
* [INTELLIJ-104](https://jira.mongodb.org/browse/INTELLIJ-104) Add support for Spring Criteria
  in/nin operator, like in `where(field).in(1, 2, 3)`
* [INTELLIJ-61](https://jira.mongodb.org/browse/INTELLIJ-61) Add support for Spring Criteria
  not operator, like in `where(field).not().is(value)`
* [INTELLIJ-49](https://jira.mongodb.org/browse/INTELLIJ-49) Add support for Spring Criteria
  update operators.
* [INTELLIJ-44](https://jira.mongodb.org/browse/INTELLIJ-44) Ability to load the Spring configuration from
  the current project's application.yml
* [INTELLIJ-91](https://jira.mongodb.org/browse/INTELLIJ-91): Ability to trigger autocompletion automatically for string constants
  in a query.
* [INTELLIJ-73](https://jira.mongodb.org/browse/INTELLIJ-73): Ability to run Java queries, both with the Java Driver and
  Spring Criteria, in the Data Explorer console.
* [INTELLIJ-93](https://jira.mongodb.org/browse/INTELLIJ-93): Inline warning when a query does not use an index and
  a quick action to generate the index template.
* [INTELLIJ-74](https://jira.mongodb.org/browse/INTELLIJ-74): Generate index template from code inspection in queries not
  covered by an index.
* [INTELLIJ-70](https://jira.mongodb.org/browse/INTELLIJ-70): Code action that allows running a Java query from within the code
  on the current data source.
* [INTELLIJ-81](https://jira.mongodb.org/browse/INTELLIJ-81): Inspections in code when a database or collection does not exist
  in the current data source.
* [INTELLIJ-43](https://jira.mongodb.org/browse/INTELLIJ-43): Extract the configured database from application.properties
  in projects with Spring Boot.
* [INTELLIJ-51](https://jira.mongodb.org/browse/INTELLIJ-51): Add an inline warning when querying a field that does not
  exist in the target collection in a Spring Criteria project.
* [INTELLIJ-53](https://jira.mongodb.org/browse/INTELLIJ-53): Add an inline warning when the type of the provided value
  for a field in a filter / update query does not match the expected type of the field in a Spring Criteria project.
* [INTELLIJ-23](https://jira.mongodb.org/browse/INTELLIJ-23): Add an inline warning when the type of the provided value
  for a field in a filter / update query does not match the expected type of the field.
* [INTELLIJ-52](https://jira.mongodb.org/browse/INTELLIJ-52): Support for autocomplete for collections specified with
  `@Document` and fields in Criteria chains.
* [INTELLIJ-24](https://jira.mongodb.org/browse/INTELLIJ-30): Supports for autocompletion in database names, collections and fields on queries. Requires
  a connection to a MongoDB cluster set up in the editor.
* [INTELLIJ-30](https://jira.mongodb.org/browse/INTELLIJ-30): Add an inline warning when querying a field that does not exist in the target
  collection.
* [INTELLIJ-29](https://jira.mongodb.org/browse/INTELLIJ-29): Shows an inlay hint near a Java query that shows in which collection the query is
  going to be run in case it could be inferred.
* [INTELLIJ-17](https://jira.mongodb.org/browse/INTELLIJ-17): Added a toolbar that allows to attach a MongoDB data source to the current editor.
  This data source is used for autocompletion and type checking.
* [INTELLIJ-14](https://jira.mongodb.org/browse/INTELLIJ-14): Send telemetry when a connection to a MongoDB Cluster fails.
* [INTELLIJ-13](https://jira.mongodb.org/browse/INTELLIJ-13): Send telemetry when successfully connected to a MongoDB Cluster.
* [INTELLIJ-12](https://jira.mongodb.org/browse/INTELLIJ-12): Notify users about telemetry, and allow them to disable it.
* [INTELLIJ-11](https://jira.mongodb.org/browse/INTELLIJ-11): Flush pending analytics events before closing the IDE.

### Changed

### Deprecated

### Removed

### Fixed
* [INTELLIJ-231](https://jira.mongodb.org/browse/INTELLIJ-231): Gracefully fail if the JVM does not give us access to plugin metadata.
* [INTELLIJ-158](https://jira.mongodb.org/browse/INTELLIJ-158): Autocomplete does not work when triggered inside valid positions but wrapped in a parseable Java Iterable.
* [INTELLIJ-157](https://jira.mongodb.org/browse/INTELLIJ-157): Unable to parse and hence inspect `Filters.and`, `Filters.or` and `Filters.nor`, in code that uses Java Driver, when argument for these method calls is a parseable Java Iterable.

### Security

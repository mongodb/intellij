package com.mongodb.jbplugin.inspections.correctness

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import com.mongodb.jbplugin.fixtures.whenQueryingCollectionSchema
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.observability.TelemetryService
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify

@IntegrationTest
class JavaDriverMongoDbFieldDoesNotExistTest {
    @ParsingTest(
        """
public FindIterable<Document> findIterableShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123"));
}
// Additional tests to verify INTELLIJ-317
public MongoCursor<Document> findIteratorShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")).iterator();
}
public MongoCursor<Document> findCursorShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")).cursor();
}
public AggregateIterable<Document> aggregateIterableShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123"))
            ));
}
public MongoCursor<Document> aggregateIteratorShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123"))
            )).iterator();
}
public MongoCursor<Document> aggregateCursorShowsWarning() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123"))
            )).cursor();
}
""",
    )
    fun `shows an inspection when the field does not exist in the current namespace`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val telemetryService = app.getService(TelemetryService::class.java)

        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
    }

    @ParsingTest(
        """
public FindIterable<Document> noInspectionForUnsupportedFilter() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(regex("nonExistingField", "123"));
}

public AggregateIterable<Document> noInspectionForUnsupportedAggregationCommand() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(
                List.of(
                    Aggregates.bucket("${'$'}field", Arrays.asList(0, 10, 20)),
                    Aggregates.bucketAuto("${'$'}field", 5),
                    Aggregates.count("totalCount")
                )
            );
}
""",
    )
    fun `shows no inspection when there is an unsupported command or operation`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
// and queries
public FindIterable<Document> inlineAndQuery() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(
                and(
                    eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> andQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var andQuery = and(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">fieldName</warning>, "123")
    );
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(andQuery);
}

public FindIterable<Document> andQueryFromAMethodCallWithFieldNameFromMethodCall() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(getAndQueryWithFieldNameFromMethodCall());
}

private Bson getAndQueryWithFieldNameFromMethodCall() {
    return and(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">getFieldName()</warning>, "123")
    );
}

private String getFieldName() {
    return "nonExistingField";
}

// or queries
public FindIterable<Document> inlineOrQuery() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(
                or(
                    eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> orQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var orQuery = or(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">fieldName</warning>, "123")
    );
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(orQuery);
}

public FindIterable<Document> orQueryFromAMethodCallWithFieldNameFromMethodCall() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(getOrQueryWithFieldNameFromMethodCall());
}

private Bson getOrQueryWithFieldNameFromMethodCall() {
    return or(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">getFieldName()</warning>, "123")
    );
}

// nor queries
public FindIterable<Document> inlineNorQuery() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(
                nor(
                    eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> norQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var norQuery = nor(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">fieldName</warning>, "123")
    );
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(norQuery);
}

public FindIterable<Document> norQueryFromAMethodCallWithFieldNameFromMethodCall() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(getNorQueryWithFieldNameFromMethodCall());
}

private Bson getNorQueryWithFieldNameFromMethodCall() {
    return nor(
        eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">getFieldName()</warning>, "123")
    );
}
""",
    )
    fun `shows an inspection when a field, referenced in different forms of a nested $and, $or and $nor query, does not exists in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> exampleAggregate() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(
                    eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123")
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#match call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> exampleGoodUnwind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.unwind(
                    "${'$'}existingField"
                )
            ));
}

public AggregateIterable<Document> exampleUnwind1() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.unwind(
                    <warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"${'$'}nonExistingField"</warning>
                )
            ));
}

public AggregateIterable<Document> exampleUnwind2() {
    String fieldName = "${'$'}nonExistingField";
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.unwind(
                    <warning descr="Field \"nonExistingField\" does not seem to exist in collection.">fieldName</warning>
                )
            ));
}

private String getField() {
    return "${'$'}nonExistingField";
}

public AggregateIterable<Document> exampleUnwind3() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.unwind(
                    <warning descr="Field \"nonExistingField\" does not seem to exist in collection.">getField()</warning>
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#unwind call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "existingField" to BsonString
                )
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> exampleAggregateInclude() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.include(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>)
                )
            ));
}

public AggregateIterable<Document> exampleAggregateExclude() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.exclude(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>)
                )
            ));
}

private Bson getAnotherProjection() {
    return Projections.exclude(<warning descr="Field \"nonExistingFieldFromReference\" does not seem to exist in collection.">"nonExistingFieldFromReference"</warning>);
}

public AggregateIterable<Document> exampleAggregateFields() {
    Bson includeProject = Projections.include(<warning descr="Field \"nonExistingFieldFromVariable\" does not seem to exist in collection.">"nonExistingFieldFromVariable"</warning>);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.fields(
                        Projections.exclude(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>),
                        includeProject,
                        getAnotherProjection()
                    )
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#project call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> exampleAggregateAscending() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.ascending(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>)
                )
            ));
}

public AggregateIterable<Document> exampleAggregateDescending() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.descending(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>)
                )
            ));
}

private Bson getAnotherSort() {
    return Sorts.descending(<warning descr="Field \"nonExistingFieldFromExternalReference\" does not seem to exist in collection.">"nonExistingFieldFromExternalReference"</warning>);
}

public AggregateIterable<Document> exampleAggregateOrderBy() {
    Bson ascendingSort = Sorts.ascending(<warning descr="Field \"nonExistingFieldFromVariable\" does not seem to exist in collection.">"nonExistingFieldFromVariable"</warning>);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.orderBy(
                        Sorts.descending(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>),
                        ascendingSort,
                        getAnotherSort()
                    )
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#sort call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> exampleAggregateOrderBy() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.addFields(
                    new Field<>("nonExistingField", "nonExistingField")
                )
            ));
}
""",
    )
    fun `does not show any inspection for Aggregates#addFields call even when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                emptyMap()
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public AggregateIterable<Document> goodGroupAggregate1() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.group(null),
                Aggregates.group("${'$'}possibleIdField"),
                Aggregates.group("${'$'}possibleIdField", Accumulators.sum("totalCount", 1)),
                Aggregates.group(
                    "${'$'}possibleIdField",
                    Accumulators.sum("totalCount", "${'$'}otherField")
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#group call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "possibleIdField" to BsonString,
                    "otherField" to BsonString,
                )
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
private String getOtherField() {
    return "${'$'}otherField";
}

public AggregateIterable<Document> goodGroupAggregate2() {
    String fieldName = "${'$'}possibleIdField";
    BsonField totalCountAcc = Accumulators.sum("totalCount", 1);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.group(
                    fieldName,
                    Accumulators.sum("totalCount", getOtherField())
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#group call with method refs and variables when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")
        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "possibleIdField" to BsonString,
                    "otherField" to BsonString,
                )
            )
        )

        fixture.enableInspections(MongoDbFieldDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }
}

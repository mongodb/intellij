package com.mongodb.jbplugin.inspections.correctness

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.observability.TelemetryService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@IntegrationTest
class JavaDriverMongoDbFieldDoesNotExistTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq(<warning descr="Field \"nonExistingField\" does not seem to exist in collection.">"nonExistingField"</warning>, "123"));
}
""",
    )
    fun `shows an inspection when the field does not exist in the current namespace`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) {
        val telemetryService = app.getService(TelemetryService::class.java)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("myDatabase", "myCollection"),
                    BsonObject(
                        mapOf(
                            "existingField" to BsonString
                        )
                    )
                )
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("myDatabase", "myCollection"),
                    BsonObject(
                        mapOf(
                            "possibleIdField" to BsonString,
                            "otherField" to BsonString,
                        )
                    )
                )
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("myDatabase", "myCollection"),
                    BsonObject(
                        mapOf(
                            "possibleIdField" to BsonString,
                            "otherField" to BsonString,
                        )
                    )
                )
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
        fixture.testHighlighting()
    }
}

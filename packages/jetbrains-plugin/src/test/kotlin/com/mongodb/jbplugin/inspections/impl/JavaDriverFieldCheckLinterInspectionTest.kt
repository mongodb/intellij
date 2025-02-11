package com.mongodb.jbplugin.inspections.impl

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonDouble
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
class JavaDriverFieldCheckLinterInspectionTest {
    @ParsingTest(
"""
    public FindIterable<Document> exampleFind() {
        return <warning descr="No connection available to run this check.">client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find()</warning>;
    }
""",
    )
    fun `shows an inspection when there is no connection attached to the current editor`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
"""
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123"));
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
    }

    @ParsingTest(
"""
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq("thisIsDouble", <warning descr="A \"String\"(type of provided value) cannot be assigned to \"double\"(type of \"thisIsDouble\")">"123"</warning>));
}
""",
    )
    fun `shows an inspection when a provided value cannot be assigned to a field because of detected type mismatch`(
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
                    BsonObject(mapOf("thisIsDouble" to BsonDouble))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
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
                    eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> andQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var andQuery = and(
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">fieldName</warning>, "123")
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
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">getFieldName()</warning>, "123")
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
                    eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> orQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var orQuery = or(
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">fieldName</warning>, "123")
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
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">getFieldName()</warning>, "123")
    );
}

// nor queries
public FindIterable<Document> inlineNorQuery() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(
                nor(
                    eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
                )
            );
}

public FindIterable<Document> norQueryFromAVariableWithVariableFieldName() {
    var fieldName = "nonExistingField";
    var norQuery = nor(
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">fieldName</warning>, "123")
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
        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">getFieldName()</warning>, "123")
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
"""
public AggregateIterable<Document> exampleAggregate() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(
                    eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
"""
public AggregateIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(
                    eq("thisIsDouble", <warning descr="A \"String\"(type of provided value) cannot be assigned to \"double\"(type of \"thisIsDouble\")">"123"</warning>)
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#match call when a provided value cannot be assigned to a field because of detected type mismatch`(
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
                    BsonObject(mapOf("thisIsDouble" to BsonDouble))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
"""
public AggregateIterable<Document> exampleAggregateInclude() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.include(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                )
            ));
}

public AggregateIterable<Document> exampleAggregateExclude() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                )
            ));
}

private Bson getAnotherProjection() {
    return Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
}

public AggregateIterable<Document> exampleAggregateFields() {
    Bson includeProject = Projections.include(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.project(
                    Projections.fields(
                        Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>),
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
"""
public AggregateIterable<Document> exampleAggregateAscending() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.ascending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                )
            ));
}

public AggregateIterable<Document> exampleAggregateDescending() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                )
            ));
}

private Bson getAnotherSort() {
    return Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
}

public AggregateIterable<Document> exampleAggregateOrderBy() {
    Bson ascendingSort = Sorts.ascending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.sort(
                    Sorts.orderBy(
                        Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>),
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
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

private String getOtherField() {
    return "${'$'}otherField";
}

public AggregateIterable<Document> goodGroupAggregate2() {
    String fieldName = "${'$'}possibleIdField";
    BsonField totalCountAcc = Accumulators.sum("totalCount", 1);
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.group(fieldName),
                Aggregates.group(fieldName, totalCountAcc),
                Aggregates.group(
                    fieldName,
                    Accumulators.sum("totalCount", getOtherField())
                )
            ));
}

private String getBadFieldName() {
    return "${'$'}nonExistentField";
}

private BsonField getAvgCountAcc() {
    return Accumulators.avg(
        "avgCount",
        <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">getBadFieldName()</warning>
    );
}

public AggregateIterable<Document> badGroupAggregate1() {
    String badFieldName = "${'$'}nonExistentField";
    BsonField avgCountAcc = Accumulators.avg(
        "avgCount",
        <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">badFieldName</warning>
    );
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.group(
                    <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">"${'$'}nonExistentField"</warning>
                ),
                Aggregates.group(
                    <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">badFieldName</warning>,
                    Accumulators.sum(
                        "totalCount",
                        <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">badFieldName</warning>
                    ),
                    Accumulators.sum(
                        "totalCount",
                        <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">getBadFieldName()</warning>
                    ),
                    avgCountAcc,
                    getAvgCountAcc(),
                    Accumulators.topN(
                        "totalCount",
                        Sorts.ascending(<warning descr="A \"int\"(type of provided value) cannot be assigned to \"String\"(type of \"otherField\")">"otherField"</warning>),
                        <warning descr="Field \"nonExistentField\" does not exist in collection \"myDatabase.myCollection\"">getBadFieldName()</warning>,
                        3
                    )
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
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
                    <warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"${'$'}nonExistingField"</warning>
                )
            ));
}

public AggregateIterable<Document> exampleUnwind2() {
    String fieldName = "${'$'}nonExistingField";
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.unwind(
                    <warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">fieldName</warning>
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
                    <warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">getField()</warning>
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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

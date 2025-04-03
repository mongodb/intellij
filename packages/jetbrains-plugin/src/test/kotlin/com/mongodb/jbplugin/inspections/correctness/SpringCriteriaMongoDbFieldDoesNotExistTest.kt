package com.mongodb.jbplugin.inspections.correctness

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaMongoDbFieldDoesNotExistTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.find(
                query(where("released").is(true)),
                Book.class
        )</warning>;
    }
    """,
    )
    fun `find_query shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksSorted() {
        <warning descr="Field \"released1\" does not seem to exist in collection."><warning descr="Field \"released2\" does not seem to exist in collection."><warning descr="Field \"released3\" does not seem to exist in collection.">template.find(
                query(where("released1").is(true)
                ).with(Sort.by("released2")
                ).with(Sort.by(Sort.Direction.DESC, "released3")),
                Book.class
        )</warning></warning></warning>;
    }
    """,
    )
    fun `find_query_with_sort shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "released";
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(true)
                )
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_match shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    String releasedFromMethodCall() {
        return "releasedFromMethodCall";
    }
    
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "releasedAsVariable";
        <warning descr="Field \"released\" does not seem to exist in collection."><warning descr="Field \"releasedAsVariable\" does not seem to exist in collection."><warning descr="Field \"releasedFromMethodCall\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(
                        "released"
                ).andInclude(
                        releasedAsVariable
                ).andExclude(
                        releasedFromMethodCall()
                )
            ),
            Book.class,
            Book.class
        )</warning></warning></warning>;
    }
    """,
    )
    fun `aggregate_with_project shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    String releasedFromMethodCall() {
        return "releasedFromMethodCall";
    }
    
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "releasedAsVariable";
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.unwind("released")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_unwind shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    String releasedFromMethodCall() {
        return "releasedFromMethodCall";
    }
    
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "releasedAsVariable";
        <warning descr="Field \"released\" does not seem to exist in collection."><warning descr="Field \"releasedAsVariable\" does not seem to exist in collection."><warning descr="Field \"releasedFromMethodCall\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(Sort.Direction.ASC, "released", releasedAsVariable, releasedFromMethodCall())
            ),
            Book.class,
            Book.class
        )</warning></warning></warning>;
    }
    """,
    )
    fun `aggregate_with_sort shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addFieldWithValueOf("addedField", "released").build()
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_addFields shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group("released")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().sum("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_sum shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().avg("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_avg shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().max("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_max shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().min("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_min shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().first("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_first shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().last("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_last shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().push("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_push shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        <warning descr="Field \"released\" does not seem to exist in collection.">template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().addToSet("released").as("acc")
            ),
            Book.class,
            Book.class
        )</warning>;
    }
    """,
    )
    fun `aggregate_with_group_addToSet shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) {
        doRunHighlightingTest(fixture)
    }

    private fun doRunHighlightingTest(fixture: CodeInsightTestFixture) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "bad_db")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("bad_db", "book"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
        fixture.testHighlighting()
    }
}

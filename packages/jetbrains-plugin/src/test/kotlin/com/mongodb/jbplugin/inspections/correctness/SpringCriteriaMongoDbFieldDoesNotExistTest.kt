package com.mongodb.jbplugin.inspections.correctness

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListCollections.Collection
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases.Database
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
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaMongoDbFieldDoesNotExistTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
                query(where(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).is(true)),
                Book.class
        );
    }
    """,
    )
    fun `find_query shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksSorted() {
        template.find(
                query(where(<warning descr="Field \"released1\" does not seem to exist in collection.">"released1"</warning>).is(true)
                ).with(Sort.by(<warning descr="Field \"released2\" does not seem to exist in collection.">"released2"</warning>)
                ).with(Sort.by(Sort.Direction.DESC, <warning descr="Field \"released3\" does not seem to exist in collection.">"released3"</warning>)),
                Book.class
        );
    }
    """,
    )
    fun `find_query_with_sort shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "released";
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).is(true)
                )
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_match shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
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
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(
                        <warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>
                ).andInclude(
                        <warning descr="Field \"releasedAsVariable\" does not seem to exist in collection.">releasedAsVariable</warning>
                ).andExclude(
                        <warning descr="Field \"releasedFromMethodCall\" does not seem to exist in collection.">releasedFromMethodCall()</warning>
                )
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_project shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
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
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.unwind(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>),
                Aggregation.unwind(<warning descr="Field \"releasedAsVariable\" does not seem to exist in collection.">releasedAsVariable</warning>),
                Aggregation.unwind(<warning descr="Field \"releasedFromMethodCall\" does not seem to exist in collection.">releasedFromMethodCall()</warning>)
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_unwind shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
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
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.Direction.ASC,
                    <warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>,
                    <warning descr="Field \"releasedAsVariable\" does not seem to exist in collection.">releasedAsVariable</warning>,
                    <warning descr="Field \"releasedFromMethodCall\" does not seem to exist in collection.">releasedFromMethodCall()</warning>
                )
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_sort shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addFieldWithValueOf("addedField", <warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).build()
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_addFields shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>)
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().sum(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_sum shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().avg(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_avg shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().max(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_max shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().min(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_min shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().first(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_first shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().last(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_last shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().push(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_push shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group().addToSet(<warning descr="Field \"released\" does not seem to exist in collection.">"released"</warning>).as("acc")
            ),
            Book.class,
            Book.class
        );
    }
    """,
    )
    fun `aggregate_with_group_addToSet shows inspection when field does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        doRunHighlightingTest(fixture)
    }

    private suspend fun doRunHighlightingTest(fixture: CodeInsightTestFixture) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "bad_db")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("bad_db")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(listOf(Collection("book", "collection")))
        )

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("bad_db", "book"), BsonObject(emptyMap()))
            ),
        )

        // fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
        fixture.testHighlighting()
    }
}

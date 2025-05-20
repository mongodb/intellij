package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListCollections.Collection
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases.Database
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup.SPRING_DATA
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class SpringCriteriaMongoDbCollectionDoesNotExistTest {
    @ParsingTest(
        setup = SPRING_DATA,
        value = """
public void allReleasedBooks() {
        template.find(
            query(
            where("released")
            .is(true)),
            <warning descr="Cannot resolve \"book\" collection in \"myDatabase\" database in the connected data source.">Book.class</warning>
        );
    }

public void exampleAggregate() {
    template.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(
                where("released").is(true)
            )
        ),
        <warning descr="Cannot resolve \"book\" collection in \"myDatabase\" database in the connected data source.">Book.class</warning>,
        Book.class
    );
}
""",
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)
        fixture.specifyDatabase(dataSource, "myDatabase")

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice), eq(null))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>(), eq(null))).thenReturn(
            ListCollections(listOf())
        )

        fixture.enableInspections(MongoDbCollectionDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = SPRING_DATA,
        value = """
public void allReleasedBooks() {
        template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        );
    }

public void exampleAggregate() {
    template.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(
                where("released").is(true)
            )
        ),
        Book.class,
        Book.class
    );
}
""",
    )
    fun `shows no inspection when the database can be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)
        fixture.specifyDatabase(dataSource, "myDatabase")

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice), eq(null))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>(), eq(null))).thenReturn(
            ListCollections(listOf(Collection("book", "collection")))
        )

        fixture.enableInspections(MongoDbCollectionDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }
}

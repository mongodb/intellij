package com.mongodb.jbplugin.inspections.correctness

import com.intellij.openapi.project.Project
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
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaMongoDbTypeMismatchTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
                query(where("released").is(<warning descr="Type \"String\" is not compatible with the type of field \"released\" (boolean).">"true"</warning>)),
                Book.class
        );
    }
    
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(<warning descr="Type \"String\" is not compatible with the type of field \"released\" (boolean).">"true"</warning>)
                )
            ),
            Book.class,
            Book.class
        );
    }
        """,
    )
    fun `shows an inspection when a provided value cannot be assigned to a field because of detected type mismatch`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "sample_books")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("sample_books")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(listOf(Collection("book", "collection")))
        )

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("sample_books", "book"),
                    BsonObject(mapOf("released" to BsonBoolean))
                )
            ),
        )

        fixture.enableInspections(MongoDbTypeMismatch::class.java)
        fixture.testHighlighting()
    }
}

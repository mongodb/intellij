package com.mongodb.jbplugin.inspections.correctness

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import com.mongodb.jbplugin.fixtures.whenQueryingCollectionSchema
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonObject
import kotlinx.coroutines.test.runTest

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

        readModelProvider.whenListDatabases("sample_books")
        readModelProvider.whenListCollections("book")
        readModelProvider.whenQueryingCollectionSchema(
            "sample_books.book",
            BsonObject(
                mapOf(
                    "released" to BsonBoolean
                )
            )
        )

        fixture.enableInspections(MongoDbTypeMismatchGlobalTool())
        fixture.testHighlighting()
    }
}

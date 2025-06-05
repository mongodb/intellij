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
class SpringCriteriaMongoDbInvalidProjectionTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(
                    <warning descr="Field \"myInclusion\" can not be included in a projection with exclusions.">"myInclusion"</warning>
                ).andExclude(
                    "myExclusion"
                )
            ),
            Book.class,
            Book.class
        );
    }
        """,
    )
    fun `shows an inspection when a projection has invalid inclusions and exclusion combinations`(
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

        fixture.enableInspections(MongoDbInvalidProjectionTool())
        fixture.testHighlighting()
    }
}

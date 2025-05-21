package com.mongodb.jbplugin.inspections.correctness

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import com.mongodb.jbplugin.fixtures.whenQueryingCollectionSchema
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonObject
import kotlinx.coroutines.test.runTest

@IntegrationTest
class JavaDriverMongoDbTypeMismatchTest {
    @ParsingTest(
        """
public AggregateIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of(
                Aggregates.match(
                    eq("thisIsDouble", <warning descr="Type \"String\" is not compatible with the type of field \"thisIsDouble\" (double).">"123"</warning>)
                )
            ));
}
""",
    )
    fun `shows an inspection for Aggregates#match call when a provided value cannot be assigned to a field because of detected type mismatch`(
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
                    "thisIsDouble" to BsonDouble
                )
            )
        )

        fixture.enableInspections(MongoDbTypeMismatchGlobalTool())
        fixture.testHighlighting()
    }
}

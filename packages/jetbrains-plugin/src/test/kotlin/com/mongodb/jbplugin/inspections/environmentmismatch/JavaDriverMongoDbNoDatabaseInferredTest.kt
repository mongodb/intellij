package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class JavaDriverMongoDbNoDatabaseInferredTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind(MongoDatabase db) {
    return <warning descr="A database could not be detected for the query.">db
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"))</warning>;
}

public void exampleAggregate(MongoDatabase db) {
    <warning descr="A database could not be detected for the query.">db
            .getCollection("myCollection")
            .aggregate(List.of())</warning>;
}
""",
    )
    fun `shows an inspection when the database cannot be inferred`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoDatabaseInferredGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    MongoDatabase db = client.getDatabase("myDatabase");
    return db
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}

public void exampleAggregate() {
    MongoDatabase db = client.getDatabase("myDatabase");
    db.getCollection("myCollection").aggregate(List.of());
}
""",
    )
    fun `does not show an inspection when the database can be inferred`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoDatabaseInferredGlobalTool())
        fixture.testHighlighting()
    }
}

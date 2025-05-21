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
class JavaDriverMongoDbNoCollectionSpecifiedTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind(String collectionName) {
    return <warning descr="No collection specified for the query.">client
            .getDatabase("myDatabase")
            .getCollection(collectionName)
            .find(eq("nonExistingField", "123"))</warning>;
}

public void exampleAggregate(String collectionName) {
    <warning descr="No collection specified for the query.">client
            .getDatabase("myDatabase")
            .getCollection(collectionName)
            .aggregate(List.of())</warning>;
}
""",
    )
    fun `shows an inspection when the collection cannot be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoCollectionSpecifiedGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    String collectionName = "myCollection";
    return client
            .getDatabase("myDatabase")
            .getCollection(collectionName)
            .find(eq("nonExistingField", "123"));
}

public void exampleAggregate() {
    String collectionName = "myCollection";
    client.getDatabase("myDatabase")
        .getCollection(collectionName)
        .aggregate(List.of());
}
""",
    )
    fun `does not show an inspection when the collection can be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoCollectionSpecifiedGlobalTool())
        fixture.testHighlighting()
    }
}

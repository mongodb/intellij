package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases.Database
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(MongoDbNoCollectionSpecified::class.java)
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
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(MongoDbNoCollectionSpecified::class.java)
        fixture.testHighlighting()
    }
}

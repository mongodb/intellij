package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListCollections.Collection
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
class JavaDriverMongoDbCollectionDoesNotExistTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return <warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"))</warning>;
}

public void exampleAggregate() {
    <warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of())</warning>;
}
""",
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("myDb")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(MongoDbCollectionDoesNotExist::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}

public void exampleAggregate() {
    client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .aggregate(List.of());
}
""",
    )
    fun `shows no inspection when the collection exists in the current data source`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(listOf(Collection("myCollection", "collection")))
        )

        fixture.enableInspections(MongoDbCollectionDoesNotExist::class.java)
        fixture.testHighlighting()
    }
}

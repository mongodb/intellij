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
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class JavaDriverMongoDbCollectionDoesNotExistTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">"myCollection"</warning>)
            .find(eq("nonExistingField", "123"));
}

public FindIterable<Document> exampleFind1() {
    String collName = "myCollection";
    return client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">collName</warning>)
            .find(eq("nonExistingField", "123"));
}

String getCollection() {
    return "myCollection";
}

public FindIterable<Document> exampleFind2() {
    return client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">getCollection()</warning>)
            .find(eq("nonExistingField", "123"));
}

public void exampleAggregate() {
    client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">"myCollection"</warning>)
            .aggregate(List.of());
}

public void exampleAggregate1() {
    String collName = "myCollection";
    client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">collName</warning>)
            .aggregate(List.of());
}

public void exampleAggregate2() {
    client.getDatabase("myDatabase")
            .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">getCollection()</warning>)
            .aggregate(List.of());
}
""",
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(Database("myDatabase")))
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
    var x = client.getDatabase("myDatabase")
        .getCollection(
            <warning descr="Cannot resolve \"randomCollection\" collection in \"myDatabase\" database in the connected data source.">"randomCollection"</warning>
        ).find(eq("nonExistingField", "123"));
    
    return client.getDatabase("myDatabase")
            .getCollection(
                <warning descr="Cannot resolve \"anotherRandomCollection\" collection in \"myDatabase\" database in the connected data source.">"anotherRandomCollection"</warning>
            ).find(eq("nonExistingField", "123"));
}
""",
    )
    fun `shows collection not existing insight correctly when there are multiple queries in the same method`(
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
    ) = runTest {
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

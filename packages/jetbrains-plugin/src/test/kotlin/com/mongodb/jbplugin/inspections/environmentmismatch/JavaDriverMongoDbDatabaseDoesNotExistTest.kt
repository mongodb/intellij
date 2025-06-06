package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListDatabases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class JavaDriverMongoDbDatabaseDoesNotExistTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">"myDatabase"</warning>)
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}

public FindIterable<Document> exampleFind1() {
    String dbName = "myDatabase";
    return client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">dbName</warning>)
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}

String getDatabaseName() {
    return "myDatabase";
}

public FindIterable<Document> exampleFind2() {
    return client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">getDatabaseName()</warning>)
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}

public void exampleAggregate() {
    client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">"myDatabase"</warning>)
            .getCollection("myCollection")
            .aggregate(List.of());
}

public void exampleAggregate1() {
    String dbName = "myDatabase";
    client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">dbName</warning>)
            .getCollection("myCollection")
            .aggregate(List.of());
}

public void exampleAggregate2() {
    client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">getDatabaseName()</warning>)
            .getCollection("myCollection")
            .aggregate(List.of());
}
""",
    )
    fun `shows an inspection when the database does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases(databases = emptyArray())

        fixture.enableInspections(MongoDbDatabaseDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    var x = client.getDatabase(
        <warning descr="Cannot resolve \"bingo\" database reference in the connected data source.">"bingo"</warning>
    ).getCollection("myCollection").find(eq("nonExistingField", "123"));
    
    return client.getDatabase(
        <warning descr="Cannot resolve \"bango\" database reference in the connected data source.">"bango"</warning>
    ).getCollection("myCollection").find(eq("nonExistingField", "123"));
}
""",
    )
    fun `shows database not existing insight correctly when there are multiple queries in the same method`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")

        fixture.enableInspections(MongoDbDatabaseDoesNotExistGlobalTool())
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
    fun `shows no inspection when the database exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModelProvider.whenListDatabases("myDatabase")

        fixture.enableInspections(MongoDbDatabaseDoesNotExistGlobalTool())
        fixture.testHighlighting()
    }
}

package com.mongodb.jbplugin.inlays

import com.intellij.codeInsight.hints.InlineInlayRenderer
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenExplainQuery
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@IntegrationTest
class MongoDbQueryIndexStatusInlayTest {
    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `does not show any inlay when the database does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase1")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.CollectionScan)

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `does not show any inlay when the collection does not exist`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase1")
        readModel.whenListCollections("nonExistingCollection")
        readModel.whenExplainQuery(ExplainPlan.CollectionScan)

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Missing Index]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("name", "test"));
    }
        """,
    )
    fun `shows a collection scan inlay`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.CollectionScan)

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> No Filters]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `shows a no filters inlay`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.IndexScan("my_index"))

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Index Scan]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("name", "test"));
    }
        """,
    )
    fun `shows an index scan inlay`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.IndexScan("my_index"))

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Ineffective Index Scan]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("name", "test"));
    }
        """,
    )
    fun `shows an ineffective index scan inlay`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.IneffectiveIndexUsage("my_index"))

        testIndexInlay(fixture)
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Dynamic Query]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("name", "test"));
    }
        """,
    )
    fun `shows queries that could not be run`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        readModel.whenListDatabases("myDatabase")
        readModel.whenListCollections("myCollection")
        readModel.whenExplainQuery(ExplainPlan.NotRun)

        testIndexInlay(fixture)
    }

    /**
     * We use this because testInlays() without parameters only tests
     * parameter inlays, and that's not our use case.
     */
    private fun TestScope.testIndexInlay(fixture: CodeInsightTestFixture) {
        eventually {
            fixture.testInlays({
                (it.renderer as InlineInlayRenderer).toString()
            }, {
                it.renderer is InlineInlayRenderer
            })
        }
    }
}

package com.mongodb.jbplugin.inlays

import com.intellij.codeInsight.hints.InlineInlayRenderer
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

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
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)
        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase1"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.CollectionScan))

        fixture.testIndexInlay()
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
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)
        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection1", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.CollectionScan))

        fixture.testIndexInlay()
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Collection Scan]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `shows a collection scan inlay`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)
        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.CollectionScan))

        fixture.testIndexInlay()
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Index Scan]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `shows an index scan inlay`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.IndexScan("my_index")))

        fixture.testIndexInlay()
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Ineffective Index Scan]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `shows an ineffective index scan inlay`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.IneffectiveIndexUsage("my_index")))

        fixture.testIndexInlay()
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        <hint text="[<image> Dynamic Query]"/>client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `shows queries that could not be run`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModel) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        whenever(readModel.slice(eq(dataSource), any<ListDatabases.Slice>()))
            .thenReturn(
                ListDatabases(
                    listOf(ListDatabases.Database("myDatabase"))
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ListCollections.Slice>()))
            .thenReturn(
                ListCollections(
                    listOf(
                        ListCollections.Collection("myCollection", "collection")
                    )
                )
            )

        whenever(readModel.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>()))
            .thenReturn(ExplainQuery(ExplainPlan.NotRun))

        fixture.testIndexInlay()
    }

    /**
     * We use this because testInlays() without parameters only tests
     * parameter inlays, and that's not our use case.
     */
    private fun CodeInsightTestFixture.testIndexInlay() {
        testInlays({
            (it.renderer as InlineInlayRenderer).toString()
        }, {
            it.renderer is InlineInlayRenderer
        })
    }
}

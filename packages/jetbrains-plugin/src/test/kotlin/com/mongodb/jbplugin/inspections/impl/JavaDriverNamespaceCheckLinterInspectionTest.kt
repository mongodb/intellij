package com.mongodb.jbplugin.inspections.impl

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.observability.TelemetryService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@IntegrationTest
class JavaDriverNamespaceCheckLinterInspectionTest {
    @ParsingTest(
"""
public FindIterable<Document> exampleFind() {
    return client.getDatabase(<warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">"myDatabase"</warning>)
            .getCollection("myCollection")
            .find(eq("nonExistingField", "123"));
}
""",
    )
    fun `shows an inspection when the database does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        value = """

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">"myCollection"</warning>)
                .find(eq("nonExistingField", "123"));
    }
        """,
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) {
        val telemetryService = app.getService(TelemetryService::class.java)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
    }
}

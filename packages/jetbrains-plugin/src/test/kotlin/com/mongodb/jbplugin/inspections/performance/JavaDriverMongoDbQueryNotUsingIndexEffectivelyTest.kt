package com.mongodb.jbplugin.inspections.performance

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
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
class JavaDriverMongoDbQueryNotUsingIndexEffectivelyTest {
    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find();
}
""",
    )
    fun `does not show an inspection when the query is a collscan`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.CollectionScan)
        )

        fixture.enableInspections(MongoDbQueryNotUsingIndexEffectively::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        """
public FindIterable<Document> exampleFind() {
    return <warning descr="Query does not use an index effectively.">client.getDatabase("myDatabase")
            .getCollection("myCollection")
            .find()</warning>;
}
""",
    )
    fun `shows an inspection when the query has an ineffective index usage`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) {
        val telemetryService = app.getService(TelemetryService::class.java)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.IneffectiveIndexUsage(""))
        )

        fixture.enableInspections(MongoDbQueryNotUsingIndexEffectively::class.java)
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
    }
}

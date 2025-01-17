package com.mongodb.jbplugin.inspections.impl

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.observability.TelemetryService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@CodeInsightTest
@Suppress("TOO_LONG_FUNCTION", "LONG_LINE")
class JavaDriverIndexCheckLinterInspectionTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return <warning descr="This query will run without an index. If you plan on using this query heavily in your application, you should create an index that covers this query.">client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find()</warning>;
    }
}
        
        """,
    )
    fun `shows an inspection when the query is a collscan`(
        app: Application,
        fixture: CodeInsightTestFixture,
    ) {
        val telemetryService = app.getService(TelemetryService::class.java)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.CollectionScan)
        )

        fixture.enableInspections(IndexCheckInspectionBridge::class.java)
        fixture.testHighlighting()

        verify(telemetryService, atLeastOnce()).sendEvent(any())
    }
}

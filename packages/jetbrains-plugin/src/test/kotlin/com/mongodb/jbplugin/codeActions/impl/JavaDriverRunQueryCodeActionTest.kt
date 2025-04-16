package com.mongodb.jbplugin.codeActions.impl

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class JavaDriverRunQueryCodeActionTest {
    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `does not show a gutter icon if not connected`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)
        val gutters = fixture.findAllGutters()
        assertTrue(gutters.isEmpty())
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
        """,
    )
    fun `does show a gutter icon if connected`(
        application: Application,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        val gutters = fixture.findAllGutters()
        assertEquals(1, gutters.size)

        val gutter = gutters.first()
        assertEquals(Icons.runQueryGutter, gutter.icon)

        application.runReadAction {
            assertEquals(CodeActionsMessages.message("code.action.run.query"), gutter.tooltipText)
        }
    }
}

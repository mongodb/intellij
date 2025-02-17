package com.mongodb.jbplugin.codeActions.impl

import com.intellij.openapi.application.Application
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@IntegrationTest
class SpringCriteriaRunQueryCodeActionTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(query(where("released").is(true)), Book.class);
    }
        """,
    )
    fun `does show a gutter icon if not connected`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(SpringCriteriaDialect)
        val gutters = fixture.findAllGutters()
        assertTrue(gutters.isNotEmpty())
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(query(where("released").is(true)), Book.class);
    }
        """,
    )
    fun `does show a gutter icon if connected`(
        application: Application,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        val gutters = fixture.findAllGutters()
        assertNotEquals(0, gutters.size)

        val gutter = gutters.find { it.icon == Icons.runQueryGutter }
        assertNotNull(gutter)

        application.runReadAction {
            assertEquals(CodeActionsMessages.message("code.action.run.query"), gutter!!.tooltipText)
        }
    }
}

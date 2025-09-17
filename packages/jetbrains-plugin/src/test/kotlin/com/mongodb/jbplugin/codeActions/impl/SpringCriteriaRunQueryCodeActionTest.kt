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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `does not show the run query gutter icon if not connected`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(SpringCriteriaDialect)
        val gutters = fixture.findAllGutters()
        assertNull(gutters.find { it.icon == Icons.instance.runQueryGutter })
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(query(where("released").is(true)), Book.class);
    }
        """,
    )
    fun `does show a run query gutter icon if connected`(
        application: Application,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        val gutters = fixture.findAllGutters()
        assertNotEquals(0, gutters.size)

        val gutter = gutters.find { it.icon == Icons.instance.runQueryGutter }
        assertNotNull(gutter)

        application.runReadAction {
            assertEquals(CodeActionsMessages.message("code.action.run.query"), gutter!!.tooltipText)
        }
    }
}

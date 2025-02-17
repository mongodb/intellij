package com.mongodb.jbplugin.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.meta.service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@IntegrationTest
class CachedQueryServiceTest {
    @ParsingTest(
        """
        public void query1() {
            return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(Filters.gt("_id", 1));
        }
        
        public void query2() {
            return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(Filters.eq("title", "foo"));
        }
        
        public void query3() {
            return client.getDatabase("myDatabase")
                .getCollection("myOtherCollection")
                .find(Filters.lt("bla", "blo"));
        }
      """
    )
    fun `does return all queries given an existing query if in the same namespace`(
        fixture: CodeInsightTestFixture
    ) = ApplicationManager.getApplication().runReadAction {
        val cachedQueryService by fixture.project.service<CachedQueryService>()

        val query1 = cachedQueryService.queryAt(fixture.queryOnMethod("query1"))!!
        val query2 = cachedQueryService.queryAt(fixture.queryOnMethod("query2"))!!
        val query3 = cachedQueryService.queryAt(fixture.queryOnMethod("query3"))!!

        val siblingsOfQuery1 = cachedQueryService.allSiblingsOf(query1)
        assertEquals(1, siblingsOfQuery1.size)
        assertNotNull(
            siblingsOfQuery1.firstOrNull {
                it.source.text == query2.source.text
            },
            "query2 was not found as a sibling of query1 ${query2.source.text}"
        )

        assertNull(
            siblingsOfQuery1.firstOrNull {
                it.source.text == query3.source.text
            },
            "query3 was found as a sibling of query1: ${query3.source.text}"
        )
    }

    private fun CodeInsightTestFixture.queryOnMethod(method: String): PsiElement {
        val actualClass = file.childrenOfType<PsiClass>().first()
        val method = actualClass.allMethods.first { it.name == method }
        val returnExpr = PsiUtil.findReturnStatements(method).last()
        return returnExpr.returnValue!!
    }
}

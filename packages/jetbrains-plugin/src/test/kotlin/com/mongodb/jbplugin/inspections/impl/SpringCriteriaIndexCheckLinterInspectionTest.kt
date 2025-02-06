package com.mongodb.jbplugin.inspections.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.*
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaIndexCheckLinterInspectionTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        <warning descr="This query will run without an index. If you plan on using this query heavily in your application, you should create an index that covers this query.">template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        )</warning>;
    }
}
        
        """,
    )
    fun `shows an inspection when the query is a collscan`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.CollectionScan)
        )

        fixture.enableInspections(IndexCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

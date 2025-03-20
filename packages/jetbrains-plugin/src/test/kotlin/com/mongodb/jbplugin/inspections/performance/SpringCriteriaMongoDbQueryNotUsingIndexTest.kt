package com.mongodb.jbplugin.inspections.performance

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaMongoDbQueryNotUsingIndexTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        <warning descr="Query does not use an index.">template.find(
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

        fixture.enableInspections(MongoDbQueryNotUsingIndex::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        );
    }
}
        
        """,
    )
    fun `does not show an inspection when the query has an ineffective index usage`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.IneffectiveIndexUsage)
        )

        fixture.enableInspections(MongoDbQueryNotUsingIndex::class.java)
        fixture.testHighlighting()
    }
}

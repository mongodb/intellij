package com.mongodb.jbplugin.inspections.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaNamespaceCheckLinterInspectionTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
            query(
            where("released")
            .is(true)),
            <warning descr="Cannot resolve \"book\" collection in \"myDb\" database in the connected data source.">Book.class</warning>
        );
    }
        """,
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("myDb")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(true)
                )
            ),
            <warning descr="Cannot resolve \"book\" collection in \"myDb\" database in the connected data source.">Book.class</warning>,
            Book.class
        );
    }
    
    public void allReleasedBooksVariant2() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(true)
                )
            ),
            <warning descr="Cannot resolve \"book\" collection in \"myDb\" database in the connected data source.">"book"</warning>,
            Book.class
        );
    }
        """,
    )
    fun `shows an inspection when the collection does not exist in the current data source for an aggregation`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("myDb")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup.SPRING_DATA
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class SpringCriteriaMongoDbNoDatabaseInferredTest {
    @ParsingTest(
        setup = SPRING_DATA,
        value = """
public void allReleasedBooks() {
        <warning descr="A database could not be detected for the query.">template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        )</warning>;
    }

public void exampleAggregate() {
    <warning descr="A database could not be detected for the query.">template.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(
                where("released").is(true)
            )
        ),
        Book.class,
        Book.class
    )</warning>;
}
""",
    )
    fun `shows an inspection when the database does not exist in the current data source`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoDatabaseInferredGlobalTool())
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = SPRING_DATA,
        value = """
public void allReleasedBooks() {
        template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        );
    }

public void exampleAggregate() {
    template.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(
                where("released").is(true)
            )
        ),
        Book.class,
        Book.class
    );
}
""",
    )
    fun `shows no inspection when the database can be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDb")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoDatabaseInferredGlobalTool())
        fixture.testHighlighting()
    }
}

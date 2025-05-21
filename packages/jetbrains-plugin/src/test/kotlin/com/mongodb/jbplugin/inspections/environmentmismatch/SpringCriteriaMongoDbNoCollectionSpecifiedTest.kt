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
class SpringCriteriaMongoDbNoCollectionSpecifiedTest {
    @ParsingTest(
        setup = SPRING_DATA,
        value = """
public void allReleasedBooks(Class collectionClass) {
        <warning descr="No collection specified for the query.">template.find(
            query(
            where("released")
            .is(true)),
            collectionClass
        )</warning>;
    }

public void exampleAggregate(Class collectionClass) {
    <warning descr="No collection specified for the query.">template.aggregate(
        Aggregation.newAggregation(
            Aggregation.match(
                where("released").is(true)
            )
        ),
        collectionClass,
        Book.class
    )</warning>;
}
""",
    )
    fun `shows an inspection when the collection cannot be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (_, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoCollectionSpecifiedGlobalTool())
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
    fun `shows no inspection when the collection can be detected`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDb")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListDatabases("myDb")
        readModelProvider.whenListCollections(collections = emptyArray())

        fixture.enableInspections(MongoDbNoCollectionSpecifiedGlobalTool())
        fixture.testHighlighting()
    }
}

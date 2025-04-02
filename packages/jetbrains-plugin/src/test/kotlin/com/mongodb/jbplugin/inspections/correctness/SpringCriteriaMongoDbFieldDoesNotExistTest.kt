package com.mongodb.jbplugin.inspections.correctness

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaMongoDbFieldDoesNotExistTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
                query(where(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).is(true)),
                Book.class
        );
    }
    
    public void allReleasedBooksSorted() {
        template.find(
                query(
                    where(
                        <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>
                    ).is(true)
                ).with(
                    Sort.by(
                        <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>
                    )
                ).with(
                    Sort.by(Sort.Direction.DESC, <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>)
                ),
                Book.class
        );
    }
    
    String releasedFromMethodCall() {
        return "released";
    }
    
    public void allReleasedBooksAggregate() {
        String releasedAsVariable = "released";
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).is(true)
                ),
                Aggregation.project(
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ).andInclude(
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ).andExclude(
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ),
                Aggregation.unwind(
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>
                ),
                Aggregation.sort(
                    Sort.Direction.ASC,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ).and(
                    Sort.Direction.ASC,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>
                ).and(
                    Sort.Direction.ASC,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ),
                Aggregation.sort(
                    Sort.by(
                        <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                        <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                        <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                    ).and(
                        Sort.by(
                            Sort.Direction.ASC,
                            <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                            <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                            <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                        ).reverse()
                    ).and(
                        Sort.by(
                            Sort.Order.asc(
                                <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>
                            ),
                            Sort.Order.desc(
                                <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>
                            ),
                            Sort.Order.by(
                                <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                            )
                        ).ascending()
                    ).and(
                        Sort.by(
                            List.of(
                                Sort.Order.asc(
                                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>
                                ),
                                Sort.Order.desc(
                                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>
                                ),
                                Sort.Order.by(
                                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                                )
                            )
                        ).descending()
                    )
                ),
                Aggregation.addFields()
                    // no inspection here as no field reference
                    .addFieldWithValue("addedField", "released")
                    // field reference as computed value
                    .addFieldWithValueOf("addedField", <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>)
                    .addFieldWithValueOf("addedField", <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>)
                    .addFieldWithValueOf("addedField", <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>)
                    .addFieldWithValueOf("addedField", Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>))
                    .addFieldWithValueOf("addedField", Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>))
                    .addFieldWithValueOf("addedField", Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>))
                    // no inspection here as no field reference
                    .addField("addedField").withValue("released")
                    // field reference as computed value
                    .addField("addedField").withValueOf(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>)
                    .addField("addedField").withValueOf(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>)
                    .addField("addedField").withValueOf(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>)
                    .addField("addedField").withValueOf(Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>))
                    .addField("addedField").withValueOf(Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>))
                    .addField("addedField").withValueOf(Fields.field(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>))
                    .build(),
                
                Aggregation.group(),
                Aggregation.group(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>),
                Aggregation.group(
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>,
                    <warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>
                ),
                Aggregation.group()
                    .sum(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .sum(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .sum(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .avg(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .avg(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .avg(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .max(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .max(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .max(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .min(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .min(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .min(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .first(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .first(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .first(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .last(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .last(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .last(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .push(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .push(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .push(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
                    .addToSet(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).as("accumulatedField")
                    .addToSet(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedAsVariable</warning>).as("accumulatedField")
                    .addToSet(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">releasedFromMethodCall()</warning>).as("accumulatedField")
            ),
            Book.class,
            Book.class
        );
    }
        """,
    )
    fun `shows an inspection when the field does not exist in the current namespace`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "bad_db")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("bad_db", "book"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(MongoDbFieldDoesNotExist::class.java)
        fixture.testHighlighting()
    }
}

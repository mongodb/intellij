package com.mongodb.jbplugin.inspections.impl

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.*
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class SpringCriteriaFieldCheckLinterInspectionTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        <warning descr="No connection available to run this check.">template.find(
            query(
            where("released")
            .is(true)),
            Book.class
        )</warning>;
    }
    
    public void allReleasedBooksAggregate() {
        <warning descr="No connection available to run this check.">template.aggregate(
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
    fun `shows an inspection when there is no connection attached to the current editor`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(SpringCriteriaDialect)
        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        <warning descr="No database selected to run this check.">template.find(
                query(where("released").is(true)),
                Book.class
        )</warning>;
    }
    
    public void allReleasedBooksAggregate() {
        <warning descr="No database selected to run this check.">template.aggregate(
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
    fun `shows an inspection when there is a connection but no database attached to the current editor`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("sample_mflix", "book"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

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

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
                query(where("released").is(<warning descr="A \"String\"(type of provided value) cannot be assigned to \"boolean\"(type of \"released\")">"true"</warning>)),
                Book.class
        );
    }
    
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(<warning descr="A \"String\"(type of provided value) cannot be assigned to \"boolean\"(type of \"released\")">"true"</warning>)
                )
            ),
            Book.class,
            Book.class
        );
    }
        """,
    )
    fun `shows an inspection when a provided value cannot be assigned to a field because of detected type mismatch`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "sample_books")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("sample_books", "book"),
                    BsonObject(mapOf("released" to BsonBoolean))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public void allReleasedBooks() {
        template.find(
                query(where("released").is("true")),
                Book.class
        );
    }
    
    public void allReleasedBooksAggregate() {
        template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is("true")
                )
            ),
            Book.class,
            Book.class
        );
    }
        """,
    )
    fun `shows no inspection when a provided value can be assigned to a field`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "sample_books")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("sample_books", "book"),
                    BsonObject(mapOf("released" to BsonAnyOf(BsonString, BsonNull)))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.addedFieldN
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.field
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.components.HasAddedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@IntegrationTest
class AddFieldsStageParserTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    AddFieldsOperationBuilder getPartialAddFields() {
        return Aggregation.addFields();
    }
    
    AddFieldsOperation getCompleteAddFields() {
        return Aggregation.addFields().build();
    }
    
    AddFieldsOperation getChainedAddFields() {
        return Aggregation.addFields().addField().withValue().build();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        var partialAddFieldsAsVariable = Aggregation.addFields();
        AddFieldsOperation completeAddFieldsAsVariable = Aggregation.addFields().build();
        AddFieldsOperation chainedAddFieldsAsVariable = Aggregation.addFields().addFieldWithValueOf().build();
        return template.aggregate(
            Aggregation.newAggregation(
                // this call is not correct but our parser is able to parse it
                Aggregation.addFields(),
                partialAddFieldsAsVariable,
                getPartialAddFields(),
                // A complete call as it terminates with build
                Aggregation.addFields().build(),
                completeAddFieldsAsVariable,
                getCompleteAddFields(),
                // Chained builder calls but empty
                Aggregation.addFields().addFieldWithValue().build(),
                chainedAddFieldsAsVariable,
                getChainedAddFields()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty addFields stage`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForAddFieldsStagesWithConstantValue(query, 9, emptyList())
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    Strin getField() {
        return "field0";
    }
    
    Strin getStringValue() {
        return "value0";
    }
    
    int getIntValue() {
        return 1;
    }
    
    long getLongValue() {
        return 1L;
    }
    
    double getDoubleValue() {
        return 1.0;
    }
    
    boolean getBooleanValue() {
        return true;
    }
    
    AddFieldsOperation getAddFieldsOperation() {
        return Aggregation.addFields().addFieldWithValue(getField(), getStringValue()).build();
    }
    
    AddFieldsOperation getAddFieldsChain() {
        int intAsVariable = 1;
        long longAsVariable = 1L;
        double doubleAsVariable = 1.0;
        boolean booleanAsVariable = true;
        return Aggregation.addFields()
            .addFieldWithValue("field0", 1)
            .addFieldWithValue("field0", intAsVariable)
            .addFieldWithValue("field0", getIntValue())
            .addFieldWithValue("field0", 1L)
            .addFieldWithValue("field0", longAsVariable)
            .addFieldWithValue("field0", getLongValue())
            .addFieldWithValue("field0", 1.0)
            .addFieldWithValue("field0", doubleAsVariable)
            .addFieldWithValue("field0", getDoubleValue())
            .addFieldWithValue("field0", true)
            .addFieldWithValue("field0", booleanAsVariable)
            .addFieldWithValue("field0", getBooleanValue())
            .build();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldAsVariable = "field0";
        String stringValueAsVariable = "value0";
        int intAsVariable = 1;
        long longAsVariable = 1L;
        double doubleAsVariable = 1.0;
        boolean booleanAsVariable = true;
        
        AddFieldsOperation addFieldsOperationAsVariable = Aggregation.addFields()
            .addFieldWithValue(fieldAsVariable, stringValueAsVariable)
            .build();
        AddFieldsOperation addFieldsChainAsVariable = Aggregation.addFields()
            .addFieldWithValue("field0", 1)
            .addFieldWithValue("field0", intAsVariable)
            .addFieldWithValue("field0", getIntValue())
            .addFieldWithValue("field0", 1L)
            .addFieldWithValue("field0", longAsVariable)
            .addFieldWithValue("field0", getLongValue())
            .addFieldWithValue("field0", 1.0)
            .addFieldWithValue("field0", doubleAsVariable)
            .addFieldWithValue("field0", getDoubleValue())
            .addFieldWithValue("field0", true)
            .addFieldWithValue("field0", booleanAsVariable)
            .addFieldWithValue("field0", getBooleanValue())
            .build();
        
        return template.aggregate(
            Aggregation.newAggregation(
                // addFieldWithValue variants
                Aggregation.addFields().addFieldWithValue("field0", "value0"), // incorrect because no build but our parser should be able to parse this
                Aggregation.addFields().addFieldWithValue("field0", "value0").build(),
                Aggregation.addFields().addFieldWithValue(fieldAsVariable, stringValueAsVariable).build(),
                Aggregation.addFields().addFieldWithValue(getField(), getStringValue()).build(),
                addFieldsOperationAsVariable,
                getAddFieldsOperation(),
                // chained addFieldWithValue with build time constant values other than string
                Aggregation.addFields()
                    .addFieldWithValue("field0", 1)
                    .addFieldWithValue("field0", intAsVariable)
                    .addFieldWithValue("field0", getIntValue())
                    .addFieldWithValue("field0", 1L)
                    .addFieldWithValue("field0", longAsVariable)
                    .addFieldWithValue("field0", getLongValue())
                    .addFieldWithValue("field0", 1.0)
                    .addFieldWithValue("field0", doubleAsVariable)
                    .addFieldWithValue("field0", getDoubleValue())
                    .addFieldWithValue("field0", true)
                    .addFieldWithValue("field0", booleanAsVariable)
                    .addFieldWithValue("field0", getBooleanValue())
                    .build(),
                addFieldsChainAsVariable,
                getAddFieldsChain()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an addFields stage with added fields via addFieldWithValue`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val stringFieldValueExpectation =
            1 to listOf(Triple("field0", "value0", BsonAnyOf(BsonString, BsonNull)))
        val intFieldValueExpectation = Triple("field0", 1, BsonAnyOf(BsonInt32, BsonNull))
        val longFieldValueExpectation = Triple("field0", 1L, BsonAnyOf(BsonInt64, BsonNull))
        val doubleFieldValueExpectation = Triple("field0", 1.0, BsonAnyOf(BsonDouble, BsonNull))
        val booleanFieldValueExpectation = Triple("field0", true, BsonAnyOf(BsonBoolean, BsonNull))
        val chainedAddFieldExpectation = 12 to listOf(
            booleanFieldValueExpectation,
            booleanFieldValueExpectation,
            booleanFieldValueExpectation,
            doubleFieldValueExpectation,
            doubleFieldValueExpectation,
            doubleFieldValueExpectation,
            longFieldValueExpectation,
            longFieldValueExpectation,
            longFieldValueExpectation,
            intFieldValueExpectation,
            intFieldValueExpectation,
            intFieldValueExpectation,
        )
        parseAndAssertForAddFieldsStagesWithConstantValue(
            query,
            9,
            listOf(
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                chainedAddFieldExpectation,
                chainedAddFieldExpectation,
                chainedAddFieldExpectation,
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldValueAsString() {
        return "field0FromSchema";
    }
    
    Field getFieldValueAsField() {
        return Fields.field("field0FromSchema");
    }
    
    AddFieldsOperation getSingleOperation() {
        return Aggregation.addFields().addFieldWithValueOf("field0", getFieldValueAsField()).build();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldAsString = "field0FromSchema";
        Field fieldAsField = Fields.field("field0FromSchema");
        AddFieldsOperation singleOperation = Aggregation.addFields().addFieldWithValueOf("field0", "field0FromSchema").build();
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addFieldWithValueOf("field0", "field0FromSchema"), // incorrect but we should be able to parse this
                singleOperation,
                getSingleOperation(),
                Aggregation.addFields()
                    .addFieldWithValueOf("field0", fieldAsString)
                    .addFieldWithValueOf("field1", getFieldValueAsString())
                    .addFieldWithValueOf("field2", Fields.field("field0FromSchema"))
                    .addFieldWithValueOf("field3", fieldAsField)
                    .addFieldWithValueOf("field4", getFieldValueAsField())
                    .build()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an addFields stage with added fields via addFieldWithValueOf`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForAddFieldsStagesWithComputedValue(
            query,
            4,
            listOf(
                1 to listOf("field0" to "field0FromSchema"),
                1 to listOf("field0" to "field0FromSchema"),
                1 to listOf("field0" to "field0FromSchema"),
                5 to listOf(
                    "field4" to "field0FromSchema",
                    "field3" to "field0FromSchema",
                    "field2" to "field0FromSchema",
                    "field1" to "field0FromSchema",
                    "field0" to "field0FromSchema",
                )
            ),
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    Strin getField() {
        return "field0";
    }
    
    Strin getStringValue() {
        return "value0";
    }
    
    int getIntValue() {
        return 1;
    }
    
    long getLongValue() {
        return 1L;
    }
    
    double getDoubleValue() {
        return 1.0;
    }
    
    boolean getBooleanValue() {
        return true;
    }
    
    AddFieldsOperation getAddFieldsOperation() {
        return Aggregation.addFields().addField(getField()).withValue(getStringValue()).build();
    }
    
    AddFieldsOperation getAddFieldsChain() {
        int intAsVariable = 1;
        long longAsVariable = 1L;
        double doubleAsVariable = 1.0;
        boolean booleanAsVariable = true;
        return Aggregation.addFields()
            .addField("field0").withValue(1)
            .addField("field0").withValue(intAsVariable)
            .addField("field0").withValue(getIntValue())
            .addField("field0").withValue(1L)
            .addField("field0").withValue(longAsVariable)
            .addField("field0").withValue(getLongValue())
            .addField("field0").withValue(1.0)
            .addField("field0").withValue(doubleAsVariable)
            .addField("field0").withValue(getDoubleValue())
            .addField("field0").withValue(true)
            .addField("field0").withValue(booleanAsVariable)
            .addField("field0").withValue(getBooleanValue())
            .build();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldAsVariable = "field0";
        String stringValueAsVariable = "value0";
        int intAsVariable = 1;
        long longAsVariable = 1L;
        double doubleAsVariable = 1.0;
        boolean booleanAsVariable = true;
        
        AddFieldsOperation addFieldsOperationAsVariable = Aggregation.addFields()
            .addField(fieldAsVariable).withValue(stringValueAsVariable)
            .build();

        AddFieldsOperation addFieldsChainAsVariable = Aggregation.addFields()
            .addField("field0").withValue(1)
            .addField("field0").withValue(intAsVariable)
            .addField("field0").withValue(getIntValue())
            .addField("field0").withValue(1L)
            .addField("field0").withValue(longAsVariable)
            .addField("field0").withValue(getLongValue())
            .addField("field0").withValue(1.0)
            .addField("field0").withValue(doubleAsVariable)
            .addField("field0").withValue(getDoubleValue())
            .addField("field0").withValue(true)
            .addField("field0").withValue(booleanAsVariable)
            .addField("field0").withValue(getBooleanValue())
            .build();
        
        return template.aggregate(
            Aggregation.newAggregation(
                // addFieldWithValue variants
                Aggregation.addFields().addField("field0").withValue("value0"), // incorrect because no build but our parser should be able to parse this
                Aggregation.addFields().addField("field0").withValue("value0").build(),
                Aggregation.addFields().addField(fieldAsVariable).withValue(stringValueAsVariable).build(),
                Aggregation.addFields().addField(getField()).withValue(getStringValue()).build(),
                addFieldsOperationAsVariable,
                getAddFieldsOperation(),
                // chained addFieldWithValue with build time constant values other than string
                Aggregation.addFields()
                    .addField("field0").withValue(1)
                    .addField("field0").withValue(intAsVariable)
                    .addField("field0").withValue(getIntValue())
                    .addField("field0").withValue(1L)
                    .addField("field0").withValue(longAsVariable)
                    .addField("field0").withValue(getLongValue())
                    .addField("field0").withValue(1.0)
                    .addField("field0").withValue(doubleAsVariable)
                    .addField("field0").withValue(getDoubleValue())
                    .addField("field0").withValue(true)
                    .addField("field0").withValue(booleanAsVariable)
                    .addField("field0").withValue(getBooleanValue())
                    .build(),
                addFieldsChainAsVariable,
                getAddFieldsChain()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an addFields stage with added fields via addField#withValue`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val stringFieldValueExpectation =
            1 to listOf(Triple("field0", "value0", BsonAnyOf(BsonString, BsonNull)))
        val intFieldValueExpectation = Triple("field0", 1, BsonAnyOf(BsonInt32, BsonNull))
        val longFieldValueExpectation = Triple("field0", 1L, BsonAnyOf(BsonInt64, BsonNull))
        val doubleFieldValueExpectation = Triple("field0", 1.0, BsonAnyOf(BsonDouble, BsonNull))
        val booleanFieldValueExpectation = Triple("field0", true, BsonAnyOf(BsonBoolean, BsonNull))
        val chainedAddFieldExpectation = 12 to listOf(
            booleanFieldValueExpectation,
            booleanFieldValueExpectation,
            booleanFieldValueExpectation,
            doubleFieldValueExpectation,
            doubleFieldValueExpectation,
            doubleFieldValueExpectation,
            longFieldValueExpectation,
            longFieldValueExpectation,
            longFieldValueExpectation,
            intFieldValueExpectation,
            intFieldValueExpectation,
            intFieldValueExpectation,
        )
        parseAndAssertForAddFieldsStagesWithConstantValue(
            query,
            9,
            listOf(
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                stringFieldValueExpectation,
                chainedAddFieldExpectation,
                chainedAddFieldExpectation,
                chainedAddFieldExpectation,
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldValueAsString() {
        return "field0FromSchema";
    }
    
    Field getFieldValueAsField() {
        return Fields.field("field0FromSchema");
    }
    
    AddFieldsOperation getSingleOperation() {
        return Aggregation.addFields().addField("field0").withValueOf(getFieldValueAsField()).build();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field = "field0";
        String fieldAsString = "field0FromSchema";
        Field fieldAsField = Fields.field("field0FromSchema");
        AddFieldsOperation singleOperation = Aggregation.addFields().addField(field).withValueOf(fieldAsString).build();
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addField("field0").withValueOf("field0FromSchema"), // incorrect but we should be able to parse this
                singleOperation,
                getSingleOperation(),
                Aggregation.addFields()
                    .addField("field0").withValueOf(fieldAsString)
                    .addField("field1").withValueOf(getFieldValueAsString())
                    .addField("field2").withValueOf(Fields.field("field0FromSchema"))
                    .addField("field3").withValueOf(fieldAsField)
                    .addField("field4").withValueOf(getFieldValueAsField())
                    .build()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an addFields stage with added fields via addField#withValueOf`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForAddFieldsStagesWithComputedValue(
            query,
            4,
            listOf(
                1 to listOf("field0" to "field0FromSchema"),
                1 to listOf("field0" to "field0FromSchema"),
                1 to listOf("field0" to "field0FromSchema"),
                5 to listOf(
                    "field4" to "field0FromSchema",
                    "field3" to "field0FromSchema",
                    "field2" to "field0FromSchema",
                    "field1" to "field0FromSchema",
                    "field0" to "field0FromSchema",
                )
            ),
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addField("something").withValueOfExpression()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should parse unidentified method calls as UNKNOWN operation`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val parsed = SpringCriteriaDialectParser.parse(query)
        val addFieldsStage = parsed.component<HasAggregation<PsiElement>>()!!.children.first()
        val addedField = addFieldsStage.component<HasAddedFields<PsiElement>>()!!.children.first()
        val addedFieldName = addedField.component<Named>()
        assertEquals(Name.UNKNOWN, addedFieldName!!.name)
    }

    companion object {
        fun parseAndAssertForAddFieldsStagesWithConstantValue(
            query: PsiExpression,
            expectedAddFieldsStagesCounts: Int,
            addFieldsStagesExpectation: List<Pair<Int, List<Triple<String, Any, BsonType>>>>
        ) {
            SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
                component<HasSourceDialect> {
                    assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
                }

                collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                    assertEquals("book", collection)
                }

                component<HasAggregation<PsiElement>> {
                    assertEquals(
                        expectedAddFieldsStagesCounts,
                        children.size,
                        "Expected $expectedAddFieldsStagesCounts aggregation stages but found ${children.size}"
                    )
                }

                addFieldsStagesExpectation.forEachIndexed {
                        stageIndex,
                        (expectedAddedFieldsCount, addedFieldsExpectations)
                    ->
                    stageN(stageIndex, Name.ADD_FIELDS) {
                        component<HasAddedFields<PsiElement>> {
                            assertEquals(
                                expectedAddedFieldsCount,
                                children.size,
                                "StageIndex $stageIndex :: Expected $expectedAddedFieldsCount added fields but found ${children.size}"
                            )
                        }

                        addedFieldsExpectations.forEachIndexed {
                                index,
                                (expectedFieldName, expectedValue, expectedType)
                            ->
                            addedFieldN(index, Name.ADD_FIELDS, stageIndex) {
                                field<HasFieldReference.Computed<PsiElement>> {
                                    assertEquals(
                                        expectedFieldName,
                                        fieldName,
                                        "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected field with name $expectedFieldName but found field with name $fieldName"
                                    )
                                }
                                value<HasValueReference.Constant<PsiElement>> {
                                    assertEquals(
                                        expectedType,
                                        type,
                                        "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected value type $expectedType but found $type"
                                    )
                                    assertEquals(
                                        expectedValue,
                                        value,
                                        "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected direction value to be $expectedValue but found $value"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun parseAndAssertForAddFieldsStagesWithComputedValue(
            query: PsiExpression,
            expectedAddFieldsStagesCounts: Int,
            addFieldsStagesExpectation: List<Pair<Int, List<Pair<String, Any>>>>
        ) {
            SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
                component<HasSourceDialect> {
                    assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
                }

                collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                    assertEquals("book", collection)
                }

                component<HasAggregation<PsiElement>> {
                    assertEquals(
                        expectedAddFieldsStagesCounts,
                        children.size,
                        "Expected $expectedAddFieldsStagesCounts aggregation stages but found ${children.size}"
                    )
                }

                addFieldsStagesExpectation.forEachIndexed {
                        stageIndex,
                        (expectedAddedFieldsCount, addedFieldsExpectations)
                    ->
                    stageN(stageIndex, Name.ADD_FIELDS) {
                        component<HasAddedFields<PsiElement>> {
                            assertEquals(
                                expectedAddedFieldsCount,
                                children.size,
                                "StageIndex $stageIndex :: Expected $expectedAddedFieldsCount added fields but found ${children.size}"
                            )
                        }

                        addedFieldsExpectations.forEachIndexed {
                                index,
                                (expectedFieldName, expectedValue)
                            ->
                            addedFieldN(index, Name.ADD_FIELDS, stageIndex) {
                                field<HasFieldReference.Computed<PsiElement>> {
                                    assertEquals(
                                        expectedFieldName,
                                        fieldName,
                                        "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected field with name $expectedFieldName but found field with name $fieldName"
                                    )
                                }
                                value<HasValueReference.Computed<PsiElement>> {
                                    type.expression.field<HasFieldReference.FromSchema<PsiElement>> {
                                        assertEquals(
                                            expectedValue,
                                            fieldName,
                                            "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected computed value to have a field reference on $expectedValue but found $fieldName"
                                        )

                                        assertTrue(
                                            displayName.startsWith("$"),
                                            "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected displayName of field in computed value reference to be \$$expectedValue but found $displayName"
                                        )

                                        assertEquals(
                                            expectedValue,
                                            fieldName,
                                            "StageIndex $stageIndex, AddedFieldIndex: $index :: Expected computed value to have a field reference on $expectedValue but found $fieldName"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

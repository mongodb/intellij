package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.accumulatedFieldN
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.field
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@IntegrationTest
class GroupStageParserTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    GroupOperation getEmptyGroup() {
        return Aggregation.group();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        GroupOperation emptyGroup = Aggregation.group();
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group(),
                emptyGroup, 
                getEmptyGroup()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty group stage`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            3,
            listOf(
                null to emptyList(),
                null to emptyList(),
                null to emptyList(),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.aggregation.GroupOperation;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getField0() {
        return "field0";
    }
    
    String getField2() {
        return "field2";
    }
    
    GroupOperation getGroupWithSingleStringField() {
        return Aggregation.group(getField0());
    }
    
    GroupOperation getGroupWithMultipleStringFields() {
        String field1 = "field1";
        return Aggregation.group("field0", field1, getField2());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field0 = "field0";
        String field1 = "field1";
        GroupOperation groupWithSingleStringField = Aggregation.group(field0);
        GroupOperation groupWithMultipleStringFields = Aggregation.group("field0", field1, getField2());
        return template.aggregate(
            Aggregation.newAggregation(
                // single _id field as string
                Aggregation.group("field0"),
                groupWithSingleStringField,
                getGroupWithSingleStringField(),

                // multiple _id fields as string
                Aggregation.group("field0", field1, getField2()),
                groupWithMultipleStringFields,
                getGroupWithMultipleStringFields()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a group stage with single _id fields passed as varargs of strings`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            6,
            listOf(
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.aggregation.GroupOperation;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getField0() {
        return "field0";
    }
    
    String getField2() {
        return "field2";
    }
    
    Fields getSingleVarFields() {
        return Fields.fields(getField0());
    }
    
    Fields getMultipleVarFields() {
        String field1 = "field1";
        return Fields.fields("field0", field1, getField2());
    }
    
    GroupOperation getGroupWithSingleField() {
        return Aggregation.group(Fields.fields(getField0()));
    }
    
    GroupOperation getGroupWithSingleVarField() {
        return Aggregation.group(getSingleVarFields());
    }
    
    GroupOperation getGroupWithMultipleField() {
        String field1 = "field1";
        return Aggregation.group(Fields.fields("field0", field1, getField2()));
    }
    
    GroupOperation getGroupWithMultipleVarField() {
        String field1 = "field1";
        return Aggregation.group(getMultipleVarFields());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field0 = "field0";
        String field1 = "field1";
        Fields singleVarFields = Fields.fields(field0);
        Fields multipleVarField = Fields.fields("field0", field1, getField2());
        GroupOperation groupWithSingleField = Aggregation.group(Fields.fields(field0));
        GroupOperation groupWithSingleVarField = Aggregation.group(singleVarFields);
        
        GroupOperation groupWithMultipleField = Aggregation.group(Fields.fields("field0", field1, getField2()));
        GroupOperation groupWithMultipleVarField = Aggregation.group(multipleVarField);
        return template.aggregate(
            Aggregation.newAggregation(
                // single _id field as Fields
                Aggregation.group(Fields.fields("field0")),
                groupWithSingleField,
                getGroupWithSingleField(),
                groupWithSingleVarField,
                getGroupWithSingleVarField(),

                // multiple _id fields as string
                Aggregation.group(Fields.fields("field0", field1, getField2())),
                groupWithMultipleField,
                getGroupWithMultipleField(),
                groupWithMultipleVarField,
                getGroupWithMultipleVarField()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a group stage with single _id fields passed as Fields object built with Fields#fields`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            10,
            listOf(
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
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
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getField0() {
        return "field0";
    }
    
    String getField2() {
        return "field2";
    }
    
    Fields.Field getSingleVarFields() {
        return Fields.field(getField0());
    }
    
    Fields getMultipleVarFields() {
        String field1 = "field1";
        return Fields.from(
            Fields.field("field0"),
            Fields.field(field1),
            Fields.field(getField2())
        );
    }
    
    GroupOperation getGroupWithSingleField() {
        return Aggregation.group(Fields.from(Fields.field(getField0())));
    }
    
    GroupOperation getGroupWithSingleVarField() {
        return Aggregation.group(Fields.from(getSingleVarFields()));
    }
    
    GroupOperation getGroupWithMultipleField() {
        String field1 = "field1";
        return Aggregation.group(Fields.from(
            Fields.field("field0"),
            Fields.field(field1),
            Fields.field(getField2())
        ));
    }
    
    GroupOperation getGroupWithMultipleVarField() {
        String field1 = "field1";
        return Aggregation.group(getMultipleVarFields());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field0 = "field0";
        String field1 = "field1";
        Fields.Field singleVarFields = Fields.field(field0);
        Fields multipleVarField = Fields.from(
            Fields.field("field0"),
            Fields.field(field1),
            Fields.field(getField2())
        );
        GroupOperation groupWithSingleField = Aggregation.group(Fields.from(Fields.field("field0")));
        GroupOperation groupWithSingleVarField = Aggregation.group(Fields.from(singleVarFields));
        
        GroupOperation groupWithMultipleField = Aggregation.group(
            Fields.from(Fields.field("field0"), Fields.field(field1), Fields.field(getField2()))
        );
        GroupOperation groupWithMultipleVarField = Aggregation.group(multipleVarField);
        return template.aggregate(
            Aggregation.newAggregation(
                // single _id field as Fields
                Aggregation.group(Fields.from(Fields.field("field0"))),
                groupWithSingleField,
                getGroupWithSingleField(),
                groupWithSingleVarField,
                getGroupWithSingleVarField(),

                // multiple _id fields as string
                Aggregation.group(
                    Fields.from(
                        Fields.field("field0"),
                        Fields.field(field1),
                        Fields.field(getField2())
                    )
                ),
                groupWithMultipleField,
                getGroupWithMultipleField(),
                groupWithMultipleVarField,
                getGroupWithMultipleVarField()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a group stage with single _id fields passed as Fields object built with Fields#from(Fields#field())`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            10,
            listOf(
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
                listOf("field0", "field1", "field2") to emptyList(),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteSumChain() {
        return Aggregation.group("idField").sum(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialSumChain = Aggregation.group("idField").sum(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeSumChain = Aggregation.group("idField").sum(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().sum(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().sum("fieldFromSchema"),
                Aggregation.group().sum("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").sum("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialSumChain,
                completeSumChain,
                getCompleteSumChain(),

                // multiple chains
                Aggregation.group()
                    .sum("fieldFromSchema").as("accumulatedField")
                    .sum("field1FromSchema").as("accumulatedField1")
                    .sum("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a sum accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.SUM, null, null)),
                null to listOf(Triple(Name.SUM, "fieldFromSchema", null)),
                null to listOf(Triple(Name.SUM, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.SUM, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.SUM, "fieldFromSchema", null)),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.SUM, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.SUM, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.SUM, "field2FromSchema", null),
                    Triple(Name.SUM, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.SUM, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").avg(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").avg(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").avg(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().avg(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().avg("fieldFromSchema"),
                Aggregation.group().avg("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").avg("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .avg("fieldFromSchema").as("accumulatedField")
                    .avg("field1FromSchema").as("accumulatedField1")
                    .avg("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an avg accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.AVG, null, null)),
                null to listOf(Triple(Name.AVG, "fieldFromSchema", null)),
                null to listOf(Triple(Name.AVG, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.AVG, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.AVG, "fieldFromSchema", null)),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.AVG, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.AVG, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.AVG, "field2FromSchema", null),
                    Triple(Name.AVG, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.AVG, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").first(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").first(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").first(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().first(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().first("fieldFromSchema"),
                Aggregation.group().first("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").first("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .first("fieldFromSchema").as("accumulatedField")
                    .first("field1FromSchema").as("accumulatedField1")
                    .first("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a first accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.FIRST, null, null)),
                null to listOf(Triple(Name.FIRST, "fieldFromSchema", null)),
                null to listOf(Triple(Name.FIRST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.FIRST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.FIRST, "fieldFromSchema", null)),
                listOf("idField") to
                    listOf(Triple(Name.FIRST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.FIRST, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.FIRST, "field2FromSchema", null),
                    Triple(Name.FIRST, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.FIRST, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").last(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").last(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").last(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().last(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().last("fieldFromSchema"),
                Aggregation.group().last("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").last("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .last("fieldFromSchema").as("accumulatedField")
                    .last("field1FromSchema").as("accumulatedField1")
                    .last("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a last accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.LAST, null, null)),
                null to listOf(Triple(Name.LAST, "fieldFromSchema", null)),
                null to listOf(Triple(Name.LAST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.LAST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.LAST, "fieldFromSchema", null)),
                listOf("idField") to
                    listOf(Triple(Name.LAST, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.LAST, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.LAST, "field2FromSchema", null),
                    Triple(Name.LAST, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.LAST, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").max(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").max(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").max(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().max(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().max("fieldFromSchema"),
                Aggregation.group().max("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").max("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .max("fieldFromSchema").as("accumulatedField")
                    .max("field1FromSchema").as("accumulatedField1")
                    .max("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a max accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.MAX, null, null)),
                null to listOf(Triple(Name.MAX, "fieldFromSchema", null)),
                null to listOf(Triple(Name.MAX, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MAX, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.MAX, "fieldFromSchema", null)),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MAX, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MAX, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.MAX, "field2FromSchema", null),
                    Triple(Name.MAX, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.MAX, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").min(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").min(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").min(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().min(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().min("fieldFromSchema"),
                Aggregation.group().min("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").min("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .min("fieldFromSchema").as("accumulatedField")
                    .min("field1FromSchema").as("accumulatedField1")
                    .min("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a min accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.MIN, null, null)),
                null to listOf(Triple(Name.MIN, "fieldFromSchema", null)),
                null to listOf(Triple(Name.MIN, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MIN, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.MIN, "fieldFromSchema", null)),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MIN, "fieldFromSchema", "accumulatedField")),
                listOf(
                    "idField"
                ) to listOf(Triple(Name.MIN, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.MIN, "field2FromSchema", null),
                    Triple(Name.MIN, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.MIN, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").push(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").push(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").push(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().push(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().push("fieldFromSchema"),
                Aggregation.group().push("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").push("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .push("fieldFromSchema").as("accumulatedField")
                    .push("field1FromSchema").as("accumulatedField1")
                    .push("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a push accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.PUSH, null, null)),
                null to listOf(Triple(Name.PUSH, "fieldFromSchema", null)),
                null to listOf(Triple(Name.PUSH, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.PUSH, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.PUSH, "fieldFromSchema", null)),
                listOf("idField") to
                    listOf(Triple(Name.PUSH, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.PUSH, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.PUSH, "field2FromSchema", null),
                    Triple(Name.PUSH, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.PUSH, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String getFieldFromSchemaVar() {
        return "fieldFromSchema";
    }
    
    Strin getAccumulatedFieldVar() {
        return "accumulatedField";
    }
    
    GroupOperation.GroupOperationBuilder getCompleteChain() {
        return Aggregation.group("idField").addToSet(getFieldFromSchemaVar()).as(getAccumulatedFieldVar());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldFromSchemaVar = "fieldFromSchema";
        String accumulatedFieldVar = "accumulatedField";
        GroupOperation.GroupOperationBuilder partialChain = Aggregation.group("idField").addToSet(fieldFromSchemaVar);
        GroupOperation.GroupOperationBuilder completeChain = Aggregation.group("idField").addToSet(fieldFromSchemaVar).as(accumulatedFieldVar);
        return template.aggregate(
            Aggregation.newAggregation(
                // an empty sum call
                Aggregation.group().addToSet(),
                // this is an incorrect group operation, but we should be able to parse it
                Aggregation.group().addToSet("fieldFromSchema"),
                Aggregation.group().addToSet("fieldFromSchema").as("accumulatedField"),
                Aggregation.group("idField").addToSet("fieldFromSchema").as("accumulatedField"),
                
                // with some variables dropped in
                partialChain,
                completeChain,
                getCompleteChain(),

                // multiple chains
                Aggregation.group()
                    .addToSet("fieldFromSchema").as("accumulatedField")
                    .addToSet("field1FromSchema").as("accumulatedField1")
                    .addToSet("field2FromSchema")
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a addToSet accumulator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForGroupStage(
            query,
            8,
            listOf(
                null to listOf(Triple(Name.ADD_TO_SET, null, null)),
                null to listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", null)),
                null to listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", null)),
                listOf("idField") to
                    listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", "accumulatedField")),
                listOf("idField") to
                    listOf(Triple(Name.ADD_TO_SET, "fieldFromSchema", "accumulatedField")),
                null to listOf(
                    Triple(Name.ADD_TO_SET, "field2FromSchema", null),
                    Triple(Name.ADD_TO_SET, "field1FromSchema", "accumulatedField1"),
                    Triple(Name.ADD_TO_SET, "fieldFromSchema", "accumulatedField"),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

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
                Aggregation.group().accumulate(),
                Aggregation.group().count(),
                Aggregation.group().stdDevPop(),
                Aggregation.group().stdDevSamp()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should parse unidentified GroupOperations as UNKNOWN operations`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val parsed = SpringCriteriaDialectParser.parse(query)
        val groupStageNodes = parsed.component<HasAggregation<PsiElement>>()!!.children
        assertEquals(4, groupStageNodes.size)
        for (node in groupStageNodes) {
            val accumulatedFields = node.component<HasAccumulatedFields<PsiElement>>()!!.children
            assertEquals(1, accumulatedFields.size)
            val fieldName = accumulatedFields.first().component<Named>()
            assertEquals(Name.UNKNOWN, fieldName!!.name)
        }
    }

    companion object {
        fun parseAndAssertForGroupStage(
            query: PsiExpression,
            expectedGroupStagesCount: Int,
            groupStagesExpectations: List<
                Pair<
                    // list of expectations for id field references or null if _id will be null
                    List<String>?,
                    // list of expectations for accumulated fields
                    List<Triple<Name, String?, String?>>
                    >
                >
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
                        expectedGroupStagesCount,
                        children.size,
                        "Expected $expectedGroupStagesCount aggregation stages but found ${children.size}"
                    )
                }

                groupStagesExpectations.forEachIndexed { stageIndex, stageExpectation ->
                    val (idFieldExpectations, accumulatedFieldExpectations) = stageExpectation
                    stageN(stageIndex, Name.GROUP) {
                        field<HasFieldReference.Inferred<PsiElement>> {
                            assertEquals(
                                "_id",
                                fieldName,
                                "StageIndex $stageIndex :: Expected inferred field reference to have name _id but found $fieldName"
                            )
                        }

                        if (idFieldExpectations == null) {
                            value<HasValueReference.Constant<PsiElement>> {
                                assertEquals(
                                    null,
                                    value,
                                    "StageIndex $stageIndex, idFieldIndex :: Expected null value for ValueReference corresponding to _id FieldReference but found $value"
                                )
                            }
                        } else {
                            idFieldExpectations.forEachIndexed { idFieldIndex, expectedFieldName ->
                                value<HasValueReference.Computed<PsiElement>> {
                                    val referencedSchemaFields =
                                        type.expression.components<HasFieldReference<PsiElement>>()
                                    assertEquals(
                                        idFieldExpectations.size,
                                        referencedSchemaFields.size,
                                        "StageIndex $stageIndex :: Expected ${idFieldExpectations.size} schema field references in Computed value reference of _id but found ${referencedSchemaFields.size}"
                                    )

                                    val referencedField = referencedSchemaFields[idFieldIndex]
                                    val reference =
                                        referencedField.reference as? HasFieldReference.FromSchema<PsiElement>
                                    assertNotNull(
                                        reference,
                                        "StageIndex $stageIndex, idFieldIndex $idFieldIndex :: No Schema field reference found"
                                    )

                                    assertEquals(
                                        reference!!.fieldName,
                                        expectedFieldName
                                    )
                                }
                            }
                        }

                        component<HasAccumulatedFields<PsiElement>> {
                            assertEquals(
                                accumulatedFieldExpectations.size,
                                children.size,
                                "StageIndex $stageIndex :: Expected ${accumulatedFieldExpectations.size} accumulated fields, found ${children.size}"
                            )
                        }

                        accumulatedFieldExpectations.forEachIndexed {
                                accumulatorIndex,
                                accumulatedFieldExpectation
                            ->
                            val (operatorName, schemaRefFieldName, accumulatedFieldName) = accumulatedFieldExpectation
                            accumulatedFieldN(
                                n = accumulatorIndex,
                                name = operatorName,
                                stageIndex = stageIndex,
                            ) {
                                if (accumulatedFieldName != null) {
                                    field<HasFieldReference.Computed<PsiElement>> {
                                        assertEquals(
                                            accumulatedFieldName,
                                            fieldName,
                                            "StageIndex $stageIndex, AccumulatorIndex $accumulatorIndex :: Expected computed field name to be $accumulatedFieldName, but found $fieldName"
                                        )
                                    }
                                }

                                if (schemaRefFieldName != null) {
                                    value<HasValueReference.Computed<PsiElement>> {
                                        val reference =
                                            type.expression.component<HasFieldReference<PsiElement>>()?.reference as? HasFieldReference.FromSchema<PsiElement>

                                        assertNotNull(
                                            reference,
                                            "StageIndex $stageIndex, AccumulatorIndex $accumulatorIndex :: Expected referenced schema field to at-least have $schemaRefFieldName, found nothing"
                                        )

                                        assertEquals(
                                            reference!!.fieldName,
                                            schemaRefFieldName
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

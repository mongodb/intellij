package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.field
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.sortN
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class SortStageParserTest {

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    SortOperation sortFromMethodCall() {
        return Aggregation.sort();
    }
    
    SortOperation sortChainFromMethodCall() {
        return Aggregation.sort().and();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        SortOperation sortAsVariable = Aggregation.sort();
        SortOperation sortChainAsVariable = Aggregation.sort().and();
        return template.aggregate(
            Aggregation.newAggregation(
                // Simple sort stage referenced differently
                Aggregation.sort(),
                sortAsVariable,
                sortFromMethodCall(),

                // Sort stage chained with a supported method
                Aggregation.sort().and(),
                sortChainAsVariable,
                sortChainFromMethodCall()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse empty sort stages and its variants`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 6,
            sortStagesExpectations = listOf(
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;import org.springframework.data.mongodb.core.mapping.Document;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    String field2() {
        return "field2";
    }
    
    SortOperation getSortNoFields() {
        return Aggregation.sort(Sort.Direction.ASC);
    }
    
    SortOperation getSortNoFieldsChained() {
        return Aggregation.sort(Sort.Direction.DESC).and(Sort.Direction.ASC);
    }
    
    SortOperation getSortWithFields() {
        String field1 = "field1";
        return Aggregation.sort(Sort.Direction.ASC, "field0", field1, field2());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field1 = "field1";
        SortOperation sortNoFields = Aggregation.sort(Sort.Direction.ASC);
        SortOperation sortNoFieldsChained = Aggregation.sort(Sort.Direction.DESC).and(Sort.Direction.ASC);
        
        SortOperation sortWithFields = Aggregation.sort(Sort.Direction.DESC, "field0", field1, field2());
        return template.aggregate(
            Aggregation.newAggregation(
                // The direction variants but without specifying fields
                // Should result in no sort criteria
                Aggregation.sort(Sort.Direction.ASC),
                sortNoFields,
                getSortNoFields(),
                Aggregation.sort(Sort.Direction.DESC).and(Sort.Direction.ASC).and(Sort.Direction.DESC),
                sortNoFieldsChained,
                getSortNoFieldsChained(),

                // The direction variants of .sort call with fields
                Aggregation.sort(Sort.Direction.ASC, "field0", field1, field2()),
                sortWithFields,
                getSortWithFields(),
                Aggregation.sort(Sort.Direction.ASC, "field0").and(Sort.Direction.DESC, field1).and(Sort.Direction.ASC, field2())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse sort method call accepting Direction enum and field names, supported chained calls and their variants`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 10,
            sortStagesExpectations = listOf(
                // Expectations for the direction variants but without specifying fields
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),
                0 to listOf(),

                // Expectations for direction variants of .sort call with fields
                3 to listOf(
                    Triple("field0", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field2", Name.ASCENDING, 1),
                ),
                3 to listOf(
                    Triple("field0", Name.DESCENDING, -1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field2", Name.DESCENDING, -1),
                ),
                3 to listOf(
                    Triple("field0", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field2", Name.ASCENDING, 1),
                ),
                3 to listOf(
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field0", Name.ASCENDING, 1),
                )
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    String getField2() {
        return "field2";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field1 = "field1";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(
                        Sort.Order.asc("field0"), // ascending
                        Sort.Order.desc(field1), // descending
                        Sort.Order.by(getField2()) // default ascending
                    )
                ),
                Aggregation.sort(
                    Sort.by(
                        List.of(
                            Sort.Order.asc("field0"), // ascending
                            Sort.Order.desc(field1), // descending
                            Sort.Order.by(getField2()) // default ascending
                        )
                    )
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse sort call provided with an Order object of List of Order objects`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 2,
            sortStagesExpectations = listOf(
                3 to listOf(
                    Triple("field0", Name.ASCENDING, 1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field2", Name.ASCENDING, 1),
                ),
                3 to listOf(
                    Triple("field0", Name.ASCENDING, 1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field2", Name.ASCENDING, 1),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    String getField2() {
        return "field2";
    }
    
    Sort getSortWithoutDirection() {
        String field1 = "field1";
        return Sort.by("field0", field1, getField2());
    }
    
    Sort getSortWithDirection() {
        String field1 = "field1";
        return Sort.by(Sort.Direction.DESC, "field0", field1, getField2());
    }
    
    Sort getSecondChainedSort() {
        return Sort.by(getField2());
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String field1 = "field1";
        Sort sortWithoutDirection = Sort.by("field0", field1, getField2());
        Sort sortWithDirection = Sort.by(Sort.Direction.DESC, "field0", field1, getField2());
        
        Sort firstChainedSort = Sort.by(Sort.Direction.DESC, field1);
        return template.aggregate(
            Aggregation.newAggregation(
                // .sort calls with Sort object
                Aggregation.sort(Sort.by("field0", field1, getField2())),
                Aggregation.sort(sortWithoutDirection),
                Aggregation.sort(getSortWithoutDirection()),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "field0", field1, getField2())),
                Aggregation.sort(sortWithDirection),
                Aggregation.sort(getSortWithDirection()),
                // .sort calls with chained .and calls accepting Sort object
                Aggregation.sort(Sort.by("field0").and(Sort.by(Sort.Direction.DESC, field1)).and(Sort.by(getField2()))),
                Aggregation.sort(Sort.by("field0").and(firstChainedSort).and(getSecondChainedSort())),
                // .sort calls with Sort object mixed with Direction enum and Order overloads
                Aggregation.sort(Sort.Direction.ASC, "field0")
                    .and(Sort.Direction.DESC, field1)
                    .and(Sort.by(Sort.Direction.ASC, getField2()))
                    .and(Sort.by(Sort.Order.desc("field3")))
                    .and(Sort.by(List.of(Sort.Order.asc("field4"))))
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse sort method call and chained and calls accepting Sort object, Direction enums or Order object and their variants`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val sortWithoutDirectionExpectation = 3 to listOf(
            Triple("field0", Name.ASCENDING, 1),
            Triple("field1", Name.ASCENDING, 1),
            Triple("field2", Name.ASCENDING, 1),
        )
        val sortWithDirectionExpectation = 3 to listOf(
            Triple("field0", Name.DESCENDING, -1),
            Triple("field1", Name.DESCENDING, -1),
            Triple("field2", Name.DESCENDING, -1),
        )
        val sortWithChainedAndCalls = 3 to listOf(
            Triple("field2", Name.ASCENDING, 1),
            Triple("field1", Name.DESCENDING, -1),
            Triple("field0", Name.ASCENDING, 1),
        )
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 9,
            sortStagesExpectations = listOf(
                sortWithoutDirectionExpectation,
                sortWithoutDirectionExpectation,
                sortWithoutDirectionExpectation,
                sortWithDirectionExpectation,
                sortWithDirectionExpectation,
                sortWithDirectionExpectation,
                sortWithChainedAndCalls,
                sortWithChainedAndCalls,
                5 to listOf(
                    Triple("field4", Name.ASCENDING, 1),
                    Triple("field3", Name.DESCENDING, -1),
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field0", Name.ASCENDING, 1),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                // Specified direction in some way but forced using chained call
                Aggregation.sort(Sort.by("field0").descending()),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "field0").descending()),
                Aggregation.sort(Sort.by(Sort.Order.desc("field0")).ascending()),
                
                // Last forced direction always takes precedence
                Aggregation.sort(Sort.by("field0").descending().ascending().ascending().descending()),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "field0").descending().ascending().ascending()),
                Aggregation.sort(Sort.by(Sort.Order.desc("field0")).descending().ascending().ascending()),

                // individual nested chains have their own forced direction
                Aggregation.sort(
                    // this chain will have a descending direction
                    Sort.by("field0").descending()
                    .and(
                        // this will have ascending
                        Sort.by("field1").descending().ascending()
                    ).and(
                        // this as well will have ascending
                        Sort.by(Sort.Direction.DESC, "field2").ascending()
                    )
                    .and(
                        // this as well will have ascending
                        Sort.by(Sort.Order.desc("field3")).ascending()
                    )
                ),

                // forced direction on individual nested chains gets overridden if the parent chain
                // forces a direction
                Aggregation.sort(
                    Sort.by("field0").descending()
                    .and(
                        Sort.by("field1").descending().ascending()
                    ).and(
                        Sort.by(Sort.Direction.DESC, "field2").ascending()
                    )
                    .and(
                        Sort.by(List.of(Sort.Order.desc("field3"))).ascending()
                    )
                    .descending() // now all the chains will have descending direction
                ),

                // reverse call have no affect if the last chained call is a forced direction
                Aggregation.sort(Sort.by("field0").reverse().ascending()),
                
                // also on the nested chains
                Aggregation.sort(
                    // this chain will have a ascending direction
                    Sort.by("field0").reverse().ascending()
                    .and(
                        // this will have ascending
                        Sort.by("field1").reverse().reverse().ascending()
                    ).and(
                        // this as well will have ascending
                        Sort.by(Sort.Direction.DESC, "field2").reverse().ascending()
                    )
                ),
                Aggregation.sort(
                    // this chain will have a descending direction
                    Sort.by("field0").descending()
                    .and(
                        // this will have ascending
                        Sort.by("field1").descending().ascending()
                    ).and(
                        // this as well will have ascending
                        Sort.by(Sort.Direction.DESC, "field2").ascending()
                    ).and(
                        Sort.by(List.of(Sort.Order.desc("field3"))).ascending()
                    ).reverse().descending() // now all the chains will have descending direction
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a forced direction using ascending, descending on Sort object and chained calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 11,
            sortStagesExpectations = listOf(
                1 to listOf(Triple("field0", Name.DESCENDING, -1)),
                1 to listOf(Triple("field0", Name.DESCENDING, -1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),

                1 to listOf(Triple("field0", Name.DESCENDING, -1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),

                4 to listOf(
                    Triple("field3", Name.ASCENDING, 1),
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field0", Name.DESCENDING, -1),
                ),

                4 to listOf(
                    Triple("field3", Name.DESCENDING, -1),
                    Triple("field2", Name.DESCENDING, -1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field0", Name.DESCENDING, -1),
                ),

                1 to listOf(Triple("field0", Name.ASCENDING, 1)),

                3 to listOf(
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field0", Name.ASCENDING, 1),
                ),

                4 to listOf(
                    Triple("field3", Name.DESCENDING, -1),
                    Triple("field2", Name.DESCENDING, -1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field0", Name.DESCENDING, -1),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    // normal reverse calls should revert the directions
                    Sort.by("field0").reverse() // descending
                ),
                Aggregation.sort(
                    Sort.by(Sort.Direction.DESC, "field0").reverse() // ascending
                ),
                Aggregation.sort(
                    // multiple reverse calls should be correct applied
                    Sort.by(Sort.Order.desc("field0")).reverse().reverse().reverse() // ascending
                ),
                Aggregation.sort(
                    // reverse should work well with earlier forced directions
                    Sort.by("field0").descending().reverse() // ascending
                ),
                Aggregation.sort(
                    Sort.by("field0").ascending().descending().reverse() // ascending
                ),
                Aggregation.sort(
                    // should be ascending
                    Sort.by("field0").descending().reverse().descending().ascending().reverse().reverse()
                ),
                Aggregation.sort(
                    // reverse should work well in nested chains
                    Sort.by("field0").reverse() // descending
                        .and(
                            Sort.by(Sort.Direction.DESC, "field1").reverse() // ascending
                        )
                        .and(
                            // should be ascending
                            Sort.by("field2").descending().reverse().descending().ascending().reverse().reverse()
                        )
                        .and(
                            // ascending
                            Sort.by(Sort.Order.desc("field3")).reverse().descending().ascending().reverse().reverse()
                        )
                ),
                Aggregation.sort(
                    // reverse on parent chain should revert the entire chain
                    Sort.by("field0").descending() // descending from nested but will be ascending finally
                        .and(
                            Sort.by(Sort.Direction.DESC, "field1").reverse() // ascending from nested but will be descending finally
                        )
                        .and(
                            // should be ascending but will be descending finally
                            Sort.by("field2").descending().reverse().descending().ascending().reverse().reverse()
                        )
                        .and(
                            // ascending but after reverse will be descending
                            Sort.by(Sort.Order.desc("field3")).reverse().descending().ascending().reverse().reverse()
                        )
                        .reverse()
                ),
                // same as above but with a bunch of forced directions and reverse on parent chain
                Aggregation.sort(
                    // reverse on parent chain should revert the entire chain
                    Sort.by("field0").descending() // descending from nested but will be ascending finally
                        .and(
                            Sort.by(Sort.Direction.DESC, "field1").reverse() // ascending from nested and ascending finally
                        )
                        .and(
                            // should be ascending and ascending also with parent effect
                            Sort.by("field2").descending().reverse().descending().ascending().reverse().reverse()
                        )
                        .and(
                            // ascending but after reverse will be descending
                            Sort.by(List.of(Sort.Order.desc("field3"))).reverse().descending().ascending().reverse().reverse()
                        )
                        .descending().reverse().descending().ascending().reverse().reverse()
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a reversed direction using reverse on sort and chained calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        parseAndAssertForSortStages(
            query = query,
            expectedSortStagesCounts = 9,
            sortStagesExpectations = listOf(
                1 to listOf(Triple("field0", Name.DESCENDING, -1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                1 to listOf(Triple("field0", Name.ASCENDING, 1)),
                4 to listOf(
                    Triple("field3", Name.ASCENDING, 1),
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field0", Name.DESCENDING, -1),
                ),
                4 to listOf(
                    Triple("field3", Name.DESCENDING, -1),
                    Triple("field2", Name.DESCENDING, -1),
                    Triple("field1", Name.DESCENDING, -1),
                    Triple("field0", Name.ASCENDING, 1),
                ),
                4 to listOf(
                    Triple("field3", Name.ASCENDING, 1),
                    Triple("field2", Name.ASCENDING, 1),
                    Triple("field1", Name.ASCENDING, 1),
                    Triple("field0", Name.ASCENDING, 1),
                ),
            )
        )
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;import org.springframework.data.mongodb.core.MongoTemplate;
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
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.unsorted()
                )
            )
        );
    }
}
"""
    )
    fun `should be able to parse unidentified methods as UNKNOWN operations`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val parsed = SpringCriteriaDialectParser.parse(query)
        val sortStage = parsed.component<HasAggregation<PsiElement>>()!!.children.first()
        val sortCriteria = sortStage.component<HasSorts<PsiElement>>()!!.children
        assertEquals(1, sortCriteria.size)
        val criterion = sortCriteria.first().component<Named>()
        assertEquals(Name.UNKNOWN, criterion!!.name)
    }

    companion object {
        fun parseAndAssertForSortStages(
            query: PsiExpression,
            expectedSortStagesCounts: Int,
            sortStagesExpectations: List<Pair<Int, List<Triple<String, Name, Int>>>>
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
                        expectedSortStagesCounts,
                        children.size,
                        "Expected $expectedSortStagesCounts aggregation stages but found ${children.size}"
                    )
                }

                sortStagesExpectations.forEachIndexed {
                        stageIndex,
                        (expectedSortCounts, sortExpectations)
                    ->
                    stageN(stageIndex, Name.SORT) {
                        component<HasSorts<PsiElement>> {
                            assertEquals(
                                expectedSortCounts,
                                children.size,
                                "StageIndex $stageIndex :: Expected $expectedSortCounts sort criteria but found ${children.size}"
                            )
                        }

                        sortExpectations.forEachIndexed {
                                index,
                                (expectedFieldName, expectedDirection, expectedDirectionValue)
                            ->
                            sortN(index, expectedDirection, stageIndex) {
                                field<HasFieldReference.FromSchema<PsiElement>> {
                                    assertEquals(
                                        expectedFieldName,
                                        fieldName,
                                        "StageIndex $stageIndex, SortCriteriaIndex: $index :: Expected field with name $expectedFieldName but found field with name $fieldName"
                                    )
                                }
                                value<HasValueReference.Inferred<PsiElement>> {
                                    assertEquals(
                                        BsonInt32,
                                        type,
                                        "StageIndex $stageIndex, SortCriteriaIndex: $index :: Expected value type BsonInt32 but found $type"
                                    )
                                    assertEquals(
                                        expectedDirectionValue,
                                        value,
                                        "StageIndex $stageIndex, SortCriteriaIndex: $index :: Expected direction value to be $expectedDirectionValue but found $value"
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

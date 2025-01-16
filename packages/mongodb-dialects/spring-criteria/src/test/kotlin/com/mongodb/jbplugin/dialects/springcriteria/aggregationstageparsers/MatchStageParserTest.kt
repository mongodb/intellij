package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.caret
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.field
import com.mongodb.jbplugin.dialects.springcriteria.filterN
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled

@IntegrationTest
class MatchStageParserTest {

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
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty match stage`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                component<HasFilter<PsiElement>> {
                    assertEquals(0, children.size)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
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
        MatchOperation matchStage = Aggregation.match();
        return template.aggregate(
            Aggregation.newAggregation(
                matchStage
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty match stage referenced as a variable`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                component<HasFilter<PsiElement>> {
                    assertEquals(0, children.size)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private MatchOperation getMatchStage() {
        return Aggregation.match();
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                getMatchStage()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty match stage referenced as a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                component<HasFilter<PsiElement>> {
                    assertEquals(0, children.size)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").is(true)
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria (eq)`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @Disabled("We do not yet support criteria referenced as a variable")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        Criteria criteria = Criteria.where("released").is(true);
        MatchOperation matchStage = Aggregation.match(
            criteria
        );
        return template.aggregate(
            Aggregation.newAggregation(
                matchStage
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria as a variable`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val parsedQuery = SpringCriteriaDialectParser.parse(query)
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @Disabled("We do not yet support criteria referenced as a method call")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private Criteria getCriteria() {
        return Criteria.where("released").is(true);
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        MatchOperation matchStage = Aggregation.match(
            getCriteria()
        );
        return template.aggregate(
            Aggregation.newAggregation(
                matchStage
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria as a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val parsedQuery = SpringCriteriaDialectParser.parse(query)
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @Disabled("We do not yet support criteria built with new Criteria() calls")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(new Criteria("released").is(true))
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria built with Criteria constructor calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").is(true).and("hidden").is(0)
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria having multiple fields`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(2, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 2 filters, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("hidden", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> { assertEquals(0, value) }
                }

                filterN(1, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> { assertEquals(true, value) }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").not().is(true)
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria (ne)`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.NOT) {
                    filterN(0, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("released", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> { assertEquals(true, value) }
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").in(true, false)
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse a match stage provided with a Criteria (in)`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(1, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.IN) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(listOf(true, false), value)
                        assertEquals(BsonArray(BsonAnyOf(BsonBoolean, BsonNull)), type)
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").is(true).andOperator(
                        Criteria.where("hidden").is(false),
                        Criteria.where("valid").is(true)
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
    fun `should be able to parse a match stage provided with a Criteria (andOperator)`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(2, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.AND) {
                    filterN(0, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("hidden", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(false, value)
                        }
                    }

                    filterN(1, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("valid", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(true, value)
                        }
                    }
                }

                filterN(1, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").is(true).orOperator(
                        Criteria.where("hidden").is(false),
                        Criteria.where("valid").is(true)
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
    fun `should be able to parse a match stage provided with a Criteria (orOperator)`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(2, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.OR) {
                    filterN(0, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("hidden", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(false, value)
                        }
                    }

                    filterN(1, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("valid", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(true, value)
                        }
                    }
                }

                filterN(1, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

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
                Aggregation.match(
                    Criteria.where("released").is(true).norOperator(
                        Criteria.where("hidden").is(false),
                        Criteria.where("valid").is(true)
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
    fun `should be able to parse a match stage provided with a Criteria (norOperator)`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.MATCH) {
                assertEquals(2, component<HasFilter<PsiElement>>()?.children?.size) {
                    "Expected at-least 1 filter, found ${component<HasFilter<PsiElement>>()?.children?.size}"
                }

                filterN(0, Name.NOR) {
                    filterN(0, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("hidden", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(false, value)
                        }
                    }

                    filterN(1, Name.EQ) {
                        field<HasFieldReference.FromSchema<PsiElement>> {
                            assertEquals("valid", fieldName)
                        }
                        value<HasValueReference.Constant<PsiElement>> {
                            assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                            assertEquals(true, value)
                        }
                    }
                }

                filterN(1, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("released", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> {
                        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), type)
                        assertEquals(true, value)
                    }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    Criteria.where("|";
    }
}
        """
    )
    fun `should be able to determine a field reference in a match criteria`(psiFile: PsiFile) {
        assertTrue(SpringCriteriaDialectParser.isReferenceToField(psiFile.caret()))
    }
}

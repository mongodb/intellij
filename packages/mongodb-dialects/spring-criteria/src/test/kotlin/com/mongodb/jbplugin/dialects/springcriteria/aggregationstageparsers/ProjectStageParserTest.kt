package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.field
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.projectionN
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class ProjectStageParserTest {

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
                Aggregation.project()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty project stage`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
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
                Aggregation.project().andInclude().andExclude()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty project stage with empty chained calls`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
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

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project("fieldA", fieldBFromVariable, fieldCFromMethodCall())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse string field names passed as varargs to Aggregation#project`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(
                    Fields.fields("fieldA", fieldBFromVariable, fieldCFromMethodCall())
                )
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse field names provided via Fields#fields to Aggregation#project`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(
                    Fields.from(
                        Fields.field("fieldA"),
                        Fields.field(fieldBFromVariable),
                        Fields.field(fieldCFromMethodCall())
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
    fun `should be able to parse field names provided via Field#field objects to Aggregation#project`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude("fieldA", fieldBFromVariable, fieldCFromMethodCall())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse chained andInclude calls with string field names passed as varargs`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude(Fields.fields("fieldA", fieldBFromVariable, fieldCFromMethodCall()))
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse chained andInclude calls with field names provided via Fields#fields`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude(
                    Fields.from(
                        Fields.field("fieldA"),
                        Fields.field(fieldBFromVariable),
                        Fields.field(fieldCFromMethodCall())
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
    fun `should be able to parse chained andInclude calls with field names provided via Fields#Field objects`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
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

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andExclude("fieldA", fieldBFromVariable, fieldCFromMethodCall())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse chained andExclude calls with string field names passed as varargs`(
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

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.EXCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(0, value)
                    }
                }

                projectionN(1, Name.EXCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(0, value)
                    }
                }

                projectionN(2, Name.EXCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(0, value)
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

import java.util.List;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    private String fieldCFromMethodCall() {
        return "fieldC";
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        String fieldBFromVariable = "fieldB";
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project("fieldA").andInclude(fieldBFromVariable).andExclude(fieldCFromMethodCall())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse multiple chained calls`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.PROJECT) {
                component<HasProjections<PsiElement>> {
                    assertEquals(3, children.size)
                }

                projectionN(0, Name.EXCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldC", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(0, value)
                    }
                }

                projectionN(1, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldB", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }

                projectionN(2, Name.INCLUDE) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("fieldA", fieldName)
                    }
                    value<HasValueReference.Inferred<PsiElement>> {
                        assertEquals(BsonInt32, type)
                        assertEquals(1, value)
                    }
                }
            }
        }
    }
}

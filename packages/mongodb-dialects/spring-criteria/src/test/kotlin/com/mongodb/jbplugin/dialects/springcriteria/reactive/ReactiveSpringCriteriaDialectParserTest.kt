package com.mongodb.jbplugin.dialects.springcriteria.reactive

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.AdditionalFile
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
import com.mongodb.jbplugin.dialects.springcriteria.sortN
import com.mongodb.jbplugin.dialects.springcriteria.updateN
import com.mongodb.jbplugin.dialects.springcriteria.value
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@IntegrationTest
class ReactiveSpringCriteriaDialectParserTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class).matching(where("released").is(true)).all();
    }
}
        """
    )
    fun `extracts a simple criteria query`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class).matching(
            Query.query(
                where("released").is(true)
            ).with(Sort.by(Sort.Direction.DESC, "year", "ratings"))
        ).all();
    }
}
        """
    )
    fun `extracts a simple criteria query even when chained with Sort using #with`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            sortN(0, Name.DESCENDING) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("year", fieldName)
                }
                value<HasValueReference.Inferred<PsiElement>> {
                    assertEquals(BsonInt32, type)
                    assertEquals(-1, value)
                }
            }

            sortN(1, Name.DESCENDING) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("ratings", fieldName)
                }
                value<HasValueReference.Inferred<PsiElement>> {
                    assertEquals(BsonInt32, type)
                    assertEquals(-1, value)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class).matching(
            Query.of(
                Query.query(
                    where("released").is(true)
                ).with(Sort.by(Sort.Direction.DESC, "year", "ratings"))
                .with(Sort.by("title"))
            )
        ).all();
    }
}
        """
    )
    fun `extracts a simple criteria query built with Query#of even when chained with Sort using #with`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            sortN(0, Name.ASCENDING) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("title", fieldName)
                }
                value<HasValueReference.Inferred<PsiElement>> {
                    assertEquals(BsonInt32, type)
                    assertEquals(1, value)
                }
            }

            sortN(1, Name.DESCENDING) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("year", fieldName)
                }
                value<HasValueReference.Inferred<PsiElement>> {
                    assertEquals(BsonInt32, type)
                    assertEquals(-1, value)
                }
            }

            sortN(2, Name.DESCENDING) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("ratings", fieldName)
                }
                value<HasValueReference.Inferred<PsiElement>> {
                    assertEquals(BsonInt32, type)
                    assertEquals(-1, value)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class).matching((where("released").is(true))).all();
    }
}
        """
    )
    fun `extracts a simple criteria query inside parenthesis`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allBooks(boolean released) {
        return template.query(Book.class).matching(where("released").is(released)).all();
    }
}
        """
    )
    fun `supports variables in values`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.EQ) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("released", fieldName)
                }
                value<HasValueReference.Runtime<PsiElement>> {
                    assertEquals(BsonBoolean, type)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allBooks(boolean released) {
        return template.find(query(where("released").is(released)), Book.class);
    }
}
        """
    )
    fun `supports inline query calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.EQ) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("released", fieldName)
                }
                value<HasValueReference.Runtime<PsiElement>> {
                    assertEquals(BsonBoolean, type)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook() {
        return template.findById("123456", Book.class);
    }
}
        """
    )
    fun `supports for findById`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_ONE) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.EQ) {
                field<HasFieldReference.FromSchema<PsiElement>> { assertEquals("_id", fieldName) }
                value<HasValueReference.Constant<PsiElement>> {
                    assertEquals(BsonAnyOf(BsonNull, BsonString), type)
                    assertEquals("123456", value)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).and("hidden").is(0))
                       .all();
    }
}
        """
    )
    fun `extracts a criteria query with multiple fields`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            filterN(1, Name.EQ) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("hidden", fieldName)
                }
                value<HasValueReference.Constant<PsiElement>> {
                    assertEquals(BsonAnyOf(BsonNull, BsonInt32), type)
                    assertEquals(0, value)
                }
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).andOperator(
                            where("hidden").is(false),
                            where("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like andOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            filterN(1, Name.AND) {
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
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).orOperator(
                            where("hidden").is(false).and("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like orOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            filterN(1, Name.OR) {
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
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).norOperator(
                            where("hidden").is(false),
                            where("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like notOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
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

            filterN(1, Name.NOR) {
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
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
        """
    )
    fun `can not refer to databases`(psiFile: PsiFile) {
        assertFalse(SpringCriteriaDialectParser.isReferenceToDatabase(psiFile))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document("|")
record Book() {}
        """
    )
    fun `can refer to a collection in a @Document annotation`(psiFile: PsiFile) {
        assertTrue(SpringCriteriaDialectParser.isReferenceToCollection(psiFile.caret()))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("|";
    }
}
        """
    )
    fun `can refer to a field in a criteria chain`(psiFile: PsiFile) {
        assertTrue(SpringCriteriaDialectParser.isReferenceToField(psiFile.caret()))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public long allReleasedBooks() {
        return template.count(where("released").is(true), Book.class);
    }
}
        """
    )
    fun `can parse count queries scenario`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        // ---- with the DSL ----
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.COUNT_DOCUMENTS) {
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

    @AdditionalFile(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> randomQuery() {
        return template."|"(where("released"));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "count;;COUNT_DOCUMENTS",
            "exactCount;;COUNT_DOCUMENTS",
            "exists;;FIND_ONE",
            "estimatedCount;;ESTIMATED_DOCUMENT_COUNT",
            "findDistinct;;DISTINCT",
            "findById;;FIND_ONE",
            "find;;FIND_MANY",
            "findAll;;FIND_MANY",
            "scroll;;FIND_MANY",
            "aggregate;;AGGREGATE",
            "insert;;INSERT_ONE",
            "insertAll;;INSERT_MANY",
            "remove;;DELETE_MANY",
            "findAllAndRemove;;DELETE_MANY",
            "replace;;REPLACE_ONE",
            "save;;UPSERT",
            "updateFirst;;UPDATE_ONE",
            "updateMulti;;UPDATE_MANY",
            "findAndRemove;;FIND_ONE_AND_DELETE",
            "findAndModify;;FIND_ONE_AND_UPDATE",
            "findAndReplace;;FIND_ONE_AND_REPLACE",
            "mapReduce;;UNKNOWN",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant commands from the driver`(
        method: String,
        expected: IsCommand.CommandType,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val query = psiFile.getQueryAtMethod("Repository", "randomQuery")
            SpringCriteriaDialectParser.parse(query).assert(expected)
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook() {
        return template.updateFirst(query(where("field").is("123456")), update("another", 1).set("third", 3f), Book.class);
    }
}
        """
    )
    fun `supports for update queries queries`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.UPDATE_ONE) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.EQ) {
                field<HasFieldReference.FromSchema<PsiElement>> { assertEquals("field", fieldName) }
                value<HasValueReference.Constant<PsiElement>> { assertEquals("123456", value) }
            }

            updateN(0, Name.SET) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("another", fieldName)
                }
                value<HasValueReference.Constant<PsiElement>> { assertEquals(1, value) }
            }

            updateN(1, Name.SET) {
                field<HasFieldReference.FromSchema<PsiElement>> { assertEquals("third", fieldName) }
                value<HasValueReference.Constant<PsiElement>> { assertEquals(3f, value) }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook() {
        return template.find(query(where("field").not().is("123456")), Book.class);
    }
}
        """
    )
    fun `supports not queries when prefixing the operation with not`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.NOT) {
                filterN(0, Name.EQ) {
                    field<HasFieldReference.FromSchema<PsiElement>> {
                        assertEquals("field", fieldName)
                    }
                    value<HasValueReference.Constant<PsiElement>> { assertEquals("123456", value) }
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook() {
        return template.find(query(where("field").in(1, 2, 3)), Book.class);
    }
}
        """
    )
    fun `supports the in operator with varargs`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.IN) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("field", fieldName)
                }
                value<HasValueReference.Constant<PsiElement>> {
                    assertEquals(listOf(1, 2, 3), value)
                    assertEquals(BsonArray(BsonAnyOf(BsonInt32, BsonNull)), type)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook() {
        return template.find(query(where("field").in(new Int[] { 1, 2, 3 })), Book.class);
    }
}
        """
    )
    fun `supports the in operator with an array of values`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.IN) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("field", fieldName)
                }
                value<HasValueReference.Runtime<PsiElement>> {
                    assertEquals(BsonArray(BsonAny), type)
                }
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import java.util.List;
import java.util.concurrent.Flow;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Book randomBook(List<String> myFieldValues) {
        return template.find(query(where("field").nin(myFieldValues)), Book.class);
    }
}
        """
    )
    fun `supports the in operator with a runtime list of parameters`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "randomBook")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            filterN(0, Name.NIN) {
                field<HasFieldReference.FromSchema<PsiElement>> {
                    assertEquals("field", fieldName)
                }
                value<HasValueReference.Runtime<PsiElement>> {
                    assertEquals(BsonArray(BsonAnyOf(BsonString, BsonNull)), type)
                }
            }
        }
    }
}

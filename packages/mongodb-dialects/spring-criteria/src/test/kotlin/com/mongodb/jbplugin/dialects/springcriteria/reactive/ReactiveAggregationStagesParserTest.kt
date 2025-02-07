package com.mongodb.jbplugin.dialects.springcriteria.reactive

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class ReactiveAggregationStagesParserTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.concurrent.Flow;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty aggregation using aggregate call with a class type provided for target collection`(
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

            component<HasAggregation<PsiElement>> {
                assertEquals(0, children.size)
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.concurrent.Flow;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(),
            "booksAsString",
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty aggregation using aggregate call with a string type provided for target collection`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("booksAsString", collection)
            }

            component<HasAggregation<PsiElement>> {
                assertEquals(0, children.size)
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.concurrent.Flow;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        Aggregation aggregation = Aggregation.newAggregation();
        return template.aggregate(
            aggregation,
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty aggregation referenced as a variable`(
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

            component<HasAggregation<PsiElement>> {
                assertEquals(0, children.size)
            }
        }
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.concurrent.Flow;

@Document
record Book() {}

class Repository {
    private final ReactiveMongoTemplate template;
    
    public Repository(ReactiveMongoTemplate template) {
        this.template = template;
    }
    
    private Aggregation getAggregation() {
        return Aggregation.newAggregation();
    }
    
    public Flow.Publisher<Book> allReleasedBooks() {
        return template.aggregate(
            getAggregation(),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an empty aggregation referenced as a return value from a method`(
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

            component<HasAggregation<PsiElement>> {
                assertEquals(0, children.size)
            }
        }
    }
}

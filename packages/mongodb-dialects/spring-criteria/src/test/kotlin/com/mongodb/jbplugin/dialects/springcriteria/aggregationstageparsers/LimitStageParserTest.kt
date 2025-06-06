package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.springcriteria.IntegrationTest
import com.mongodb.jbplugin.dialects.springcriteria.ParsingTest
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialectParser
import com.mongodb.jbplugin.dialects.springcriteria.assert
import com.mongodb.jbplugin.dialects.springcriteria.collection
import com.mongodb.jbplugin.dialects.springcriteria.component
import com.mongodb.jbplugin.dialects.springcriteria.getQueryAtMethod
import com.mongodb.jbplugin.dialects.springcriteria.stageN
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class LimitStageParserTest {
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
                Aggregation.limit()
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an limit stage with no parameter`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.LIMIT) {
                assertEquals(null, component<HasLimit>())
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
    private static final String AUTHOR = "author";
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.limit(2)
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an limit stage with a constant limit`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.LIMIT) {
                assertEquals(2, component<HasLimit>()?.limit)
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
    private static final String AUTHOR = "author";
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        int limit = 2;
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.limit(limit)
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an limit stage with a variable limit`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.LIMIT) {
                assertEquals(2, component<HasLimit>()?.limit)
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
    private static final String AUTHOR = "author";
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    int getLimit() {
        return 2;
    }
    
    public AggregationResults<Book> allReleasedBooks() {
        int limit = 2;
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.limit(getLimit())
            ),
            Book.class,
            Book.class
        );
    }
}
        """
    )
    fun `should be able to parse an limit stage with a limit from method call`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        SpringCriteriaDialectParser.parse(query).assert(IsCommand.CommandType.AGGREGATE) {
            component<HasSourceDialect> {
                assertEquals(HasSourceDialect.DialectName.SPRING_CRITERIA, name)
            }

            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("book", collection)
            }

            stageN(0, Name.LIMIT) {
                assertEquals(2, component<HasLimit>()?.limit)
            }
        }
    }
}

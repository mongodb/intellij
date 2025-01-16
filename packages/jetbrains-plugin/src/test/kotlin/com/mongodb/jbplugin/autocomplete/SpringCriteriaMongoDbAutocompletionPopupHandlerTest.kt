package com.mongodb.jbplugin.autocomplete

import com.intellij.database.util.common.containsElements
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq

@Suppress("TOO_LONG_FUNCTION")
@CodeInsightTest
class SpringCriteriaMongoDbAutocompletionPopupHandlerTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.mapping.Document;
@Document(<caret>)
record Book() {}
        """,
    )
    fun `should autocomplete collections from the current connection`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))
        ).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                    ListCollections.Collection("anotherCollection", "collection"),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "anotherCollection"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
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
        return template.query(Book.class).matching(where(<caret>
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
        return template.aggregate(Aggregation.newAggregation(), <caret>
    }
}
        """,
    )
    fun `should autocomplete collections from the current connection in an aggregate call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))
        ).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                    ListCollections.Collection("anotherCollection", "collection"),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "anotherCollection"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;import org.springframework.data.mongodb.core.mapping.Document;
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
        return template.aggregateStream(Aggregation.newAggregation(), <caret>
    }
}
        """,
    )
    fun `should autocomplete collections from the current connection in an aggregateStream call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))
        ).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                    ListCollections.Collection("anotherCollection", "collection"),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "anotherCollection"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
                Aggregation.match(where(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in a Criteria inside an aggregate call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
        return template.aggregateStream(
            Aggregation.newAggregation(
                Aggregation.match(where(<caret>))
            ),
            Book.class,
            Book.class
        ).toList();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in a Criteria inside an aggregateStream call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
                Aggregation.project(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in a Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;
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
                Aggregation.project(Fields.fields(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in fields helper passed to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;
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
                Aggregation.project(Fields.from(Fields.field(<caret>)))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in field helper passed to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
                Aggregation.project().andInclude(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;
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
                Aggregation.project().andInclude(Fields.fields(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in fields helper passed to andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;import org.springframework.data.mongodb.core.mapping.Document;
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
                Aggregation.project().andInclude(Fields.from(Fields.field(<caret>)))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in field helper passed to andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
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
                Aggregation.project().andExclude(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in andExclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }
}

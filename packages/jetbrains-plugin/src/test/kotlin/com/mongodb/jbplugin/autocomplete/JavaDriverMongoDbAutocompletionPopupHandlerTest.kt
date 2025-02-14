package com.mongodb.jbplugin.autocomplete

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@IntegrationTest
class JavaDriverMongoDbAutocompletionPopupHandlerTest {
    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase(<caret>)
    }
        """,
    )
    fun `should autocomplete databases from the current connection`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()

        `when`(readModelProvider.slice(eq(dataSource), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(
                listOf(
                    ListDatabases.Database("myDatabase1"),
                    ListDatabases.Database("myDatabase2"),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myDatabase1"
            },
        )
        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myDatabase2"
            },
        )
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection(<caret>).find();
    }
        """,
    )
    fun `should autocomplete collections from the current connection and inferred database`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()

        `when`(
            readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))
        ).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myCollection"
            },
        )
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection("myCollection")
                .find(eq(<caret>));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq(<caret>), set("x", 1));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in the filters of an update`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq("x", 1), set(<caret>, 2));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in the updates of an update`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public AggregateIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.match(eq(<caret>)
                )));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in the filters of an Aggregates#match stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.include(<caret>)
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#include of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.exclude(<caret>)
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#exclude of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.fields(
                            Projections.exclude(<caret>)
                        )
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#fields of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.ascending(<caret>)
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Sorts#ascending of an Aggregates#sort stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.descending(<caret>)
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Sorts#descending of an Aggregates#sort stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.orderBy(
                            Sorts.descending(<caret>)
                        )
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Sorts#orderBy of an Aggregates#sort stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.addFields(
                        new Field<>(<caret>)
                    )                
                ));
    }
        """,
    )
    fun `should not autocomplete fields from the current namespace in Aggregates#addFields stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.group(
                        <caret>
                    )                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace for _id expression in Aggregates#group stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.group(
                        "${'$'}year",
                        Accumulators.sum("totalMovies", <caret>)
                    )
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace for accumulator expression in Aggregates#group stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        value = """
    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.unwind(<caret>)                
                ));
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in an Aggregates#unwind stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace, 50)))
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        fixture.type('"')
        val elements = fixture.completeBasic()

        assertNotNull(
            elements.firstOrNull {
                it.lookupString == "myField"
            },
        )
    }
}

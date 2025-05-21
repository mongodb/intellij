package com.mongodb.jbplugin.autocomplete

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.assertAutocompletes
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenListDatabases
import com.mongodb.jbplugin.fixtures.whenQueryingCollectionSchema
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull

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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenListDatabases("myDatabase1", "myDatabase2")

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myDatabase1", "myDatabase2")
        }
    }

    @ParsingTest(
        value = """
    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection(<caret>);
    }
        """,
    )
    fun `should autocomplete collections from the current connection and inferred database even without a query`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")

        fixture.type('"')

        eventually {
            fixture.assertAutocompletes("myCollection")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)

        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenListDatabases("myDatabase")
        readModelProvider.whenListCollections("myCollection")

        fixture.type('"')

        eventually {
            fixture.assertAutocompletes("myCollection")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
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
    ) = runTest {
        fixture.specifyDialect(JavaDriverDialect)
        val (_, readModelProvider) = fixture.setupConnection()

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.myCollection",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField")
        }
    }
}

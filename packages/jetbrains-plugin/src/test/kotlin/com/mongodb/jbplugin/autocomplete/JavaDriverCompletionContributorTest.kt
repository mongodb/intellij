package com.mongodb.jbplugin.autocomplete

import com.intellij.database.util.common.containsElements
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@CodeInsightTest
class JavaDriverCompletionContributorTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase(<caret>)
    }
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myDatabase1"
            },
        )
        assertTrue(
            elements.containsElements {
                it.lookupString == "myDatabase2"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection("<caret>").find();
    }
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase").getCollection("myCollection")
                .find(eq("<caret>"));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq("<caret>"), set("x", 1));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq("x", 1), set("<caret>", 2));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.match(
                        eq("<caret>")
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.include("<caret>")
                    )
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.include(
                            List.of("<caret>")
                        )
                    )
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#include built with List#of of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.Arrays;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.include(
                            Arrays.asList("<caret>")
                        )
                    )
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#include built with Arrays#asList of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.exclude("<caret>")
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.exclude(
                            List.of("<caret>")
                        )
                    )
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#exclude built with List#of of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.Arrays;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.exclude(
                            Arrays.asList("<caret>")
                        )
                    )
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#exclude built with Arrays#asList of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.fields(
                            Projections.exclude("<caret>")
                        )
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.fields(
                            List.of(
                                Projections.exclude("<caret>")
                            )
                        )
                    )                
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#fields built with List#of of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.Arrays;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.fields(
                            Arrays.asList(
                                Projections.exclude("<caret>")
                            )
                        )
                    )                
                ));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in Projections#fields built with Arrays#asList of an Aggregates#project stage`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        val (dataSource, readModelProvider) = fixture.setupConnection()
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.ascending("<caret>")
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.descending("<caret>")
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.orderBy(
                            Sorts.descending("<caret>")
                        )
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.addFields(
                        new Field<>("<caret>")
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertFalse(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.group(
                        "<caret>"
                    )                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.group(
                        "${'$'}year",
                        Accumulators.sum("totalMovies", "<caret>")
                    )
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.unwind("<caret>")                
                ));
    }
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
            readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))
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

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )
    }
}

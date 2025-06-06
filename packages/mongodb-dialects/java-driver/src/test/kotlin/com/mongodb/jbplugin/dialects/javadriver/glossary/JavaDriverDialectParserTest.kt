package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.WithFile
import com.mongodb.jbplugin.dialects.javadriver.caret
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@IntegrationTest
class JavaDriverDialectParserTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document completeQuery(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
    
    public Document partialQuery(ObjectId id) {
        return this.collection.find(eq("_id", id));
    }
    
    public Document justNamespace() {
        return this.collection;
    }
}
        """,
    )
    fun `can extract collection reference`(psiFile: PsiFile) {
        val queries = listOf(
            psiFile.getQueryAtMethod("Repository", "completeQuery"),
            psiFile.getQueryAtMethod("Repository", "partialQuery"),
            psiFile.getQueryAtMethod("Repository", "justNamespace"),
        )
        for (query in queries) {
            val collectionReference = JavaDriverDialect.parser.parseCollectionReference(
                query
            ).reference as HasCollectionReference.Known<*>
            assertNotNull(
                collectionReference,
                "Collection reference should not be null for query: $query"
            )
            assertEquals(
                "simple",
                collectionReference.namespace.database,
                "Incorrect database name for query: $query"
            )
            assertEquals(
                "books",
                collectionReference.namespace.collection,
                "Incorrect collection name for query: $query"
            )
        }
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `can parse a mongodb query using the driver`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val psiManager = PsiManager.getInstance(psiFile.project)
        assertTrue(
            psiManager.areElementsEquivalent(
                query,
                JavaDriverDialectParser.attachment(query)
            )
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public Document getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `not a candidate if does not query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "getCollection")
        val psiManager = PsiManager.getInstance(psiFile.project)
        assertFalse(
            psiManager.areElementsEquivalent(
                query,
                JavaDriverDialectParser.attachment(query)
            )
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `the attachment for findOne command happens at the first() method call`(psiFile: PsiFile) {
        // Returns the value of the entire return expression
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        // The entire return value of the method will be the attachment because the entire query
        // constructs a FIND_ONE command
        assertEquals(query, JavaDriverDialectParser.attachment(query))
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public List<Document> findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).into(new ArrayList<>());
    }
}
        """,
    )
    fun `the attachment for commands other than findOne happens at the collection method call`(
        psiFile: PsiFile
    ) {
        // Returns the value of the entire return expression
        val queryWithIterableCall = psiFile.getQueryAtMethod("Repository", "findBookById")
        // The entire return expression is not a candidate for query
        val psiManager = PsiManager.getInstance(psiFile.project)
        assertFalse(
            psiManager.areElementsEquivalent(
                queryWithIterableCall,
                JavaDriverDialectParser.attachment(queryWithIterableCall)
            )
        )

        val actualQuery = PsiTreeUtil
            .findChildrenOfType(queryWithIterableCall, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("id))") }
        // Only the expressions until the actual find call is the valid candidate for query
        assertTrue(
            psiManager.areElementsEquivalent(
                actualQuery,
                JavaDriverDialectParser.attachment(actualQuery)
            )
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `can extract the namespace of a query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val knownReference = parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Known
        val command = parsedQuery.component<IsCommand>()
        val dialect = parsedQuery.component<HasSourceDialect>()
        val namespace = knownReference.namespace

        assertEquals(HasSourceDialect.DialectName.JAVA_DRIVER, dialect?.name)
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
        assertEquals(IsCommand.CommandType.FIND_ONE, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `handles gracefully when the namespace is unknown`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val unknownReference =
            parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Unknown

        assertEquals(HasCollectionReference.Unknown, unknownReference)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class BaseRepository<T> {
    private final String dbName;
    private final String collName;
    private final Class<T> docClass;
    
    protected BaseRepository(MongoClient client, String dbName, String collName, Class<T> docClass) {
        this.client = client;
        this.dbName = dbName;
        this.collName = collName;
        this.docClass = docClass;
    }
    
    protected MongoCollection<T> getCollection() {
        return client.getDatabase(dbName).getCollection(collName, docClass);
    }
}

public final class Repository extends BaseRepository<Document> {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client, String collection) {
        super(client, "myDb", collection, Document.class);
    }
    
    public Document findById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `supports inheritance chains with unknown collections`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val unknownReference =
            parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Unknown

        assertEquals(HasCollectionReference.Unknown, unknownReference)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        return this.collection.find(Filters.eq("_id", id));
    }
}
        """,
    )
    fun `can parse a basic Filters query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        var query = Filters.eq("_id", id);
        return this.collection.find(query);
    }
}
        """,
    )
    fun `can parse a basic Filters query in a variable`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> filter1(Bson schema) {
        return this.collection.find(Filters.jsonSchema(schema));
    }
}
        """,
    )
    fun `parses unidentified filters as UNKNOWN`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "filter1")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        println("parsedQuery=$parsedQuery isSupported = ${parsedQuery.isSupportedBlocking()}")
        assertEquals(Name.UNKNOWN, eq.component<Named>()!!.name)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public Document findBookById(ObjectId id) {
        return this.collection.find(Filters.eq("_id", id)).first();
    }
}
        """,
    )
    fun `can parse a basic Filters query for FIND_ONE command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val command = parsedQuery.component<IsCommand>()
        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(IsCommand.CommandType.FIND_ONE, command?.type)
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        return this.collection.find(Filters.eq(id));
    }
}
        """,
    )
    fun `can parse a query working with single parameter version of Filters#eq`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        return this.collection.find((Filters.eq("_id", id)));
    }
}
        """,
    )
    fun `can parse a basic Filters query inside parenthesis`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("myField", true));
    }
}
        """,
    )
    fun `can parse a basic Filters query with a constant parameter in a chain of calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "myField",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("myField", null));
    }
}
        """,
    )
    fun `correctly parses a nullable value reference as BsonNull type`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "myField",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonNull,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            null,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        try (var session = client.startSession()) {
            return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(session, eq("myField", null));
        }
    }
}
        """,
    )
    fun `correctly detects session parameters`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "myField",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonNull,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            null,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(and(eq("released", true), eq("hidden", false)));
    }
}
        """,
    )
    fun `supports filters built with Filters#and using varargs arguments`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasFilter.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        var query = and(released, notHidden);
        
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(query);
    }
}
        """,
    )
    fun `supports filters built with Filters#and using varargs arguments passed as a variable`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private Bson getAnd() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        return and(released, notHidden);
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(getAnd());
    }
}
        """,
    )
    fun `supports filters built with Filters#and using varargs arguments returned from a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(or(eq("released", true), eq("hidden", false)));
    }
}
        """,
    )
    fun `supports filters built with Filters#or using varargs arguments`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasFilter.children[0]
        assertEquals(Name.OR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        var query = or(released, notHidden);
        
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(query);
    }
}
        """,
    )
    fun `supports filters built with Filters#or using varargs arguments passed as a variable`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.OR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private Bson getOr() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        return or(released, notHidden);
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(getOr());
    }
}
        """,
    )
    fun `supports filters built with Filters#or using varargs arguments returned from a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.OR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(nor(eq("released", true), eq("hidden", false)));
    }
}
        """,
    )
    fun `supports filters built with Filters#nor using varargs arguments`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasFilter.children[0]
        assertEquals(Name.NOR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        var query = nor(released, notHidden);
        
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(query);
    }
}
        """,
    )
    fun `supports filters built with Filters#nor using varargs arguments passed as a variable`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.NOR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private Bson getNor() {
        var released = eq("released", true);
        var notHidden = eq("hidden", false);
        return nor(released, notHidden);
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(getNor());
    }
}
        """,
    )
    fun `supports filters built with Filters#nor using varargs arguments returned from a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.NOR, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(not(eq("released", true)));
    }
}
        """,
    )
    fun `supports the not operator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasFilter.children[0]
        assertEquals(Name.NOT, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var isReleased = eq("released", true);
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(isReleased);
    }
}
        """,
    )
    fun `supports references to variables in a query expression`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(isReleased());
    }
    
    private Document isReleased() {
        return eq("released", true);
    }
}
        """,
    )
    fun `supports to methods in a query expression`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("released", released));
    }
}
        """,
    )
    fun `supports to methods in a custom dsl as in mms`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private static final String RELEASED = "released";
    private static final String HIDDEN = "hidden";
    
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var isReleased = eq(RELEASED, true);
        var isNotHidden = eq(HIDDEN, false);
        
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(and(isReleased, isNotHidden));
    }
}
        """,
    )
    fun `supports vararg operators with references to fields in variables`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter =
            parsedQuery.component<HasFilter<Unit?>>()!!

        val and = hasFilter.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasFilter<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        try (var session = client.startSession()) {
            return client.getDatabase("myDatabase")
                    .getCollection("myCollection")
                    .updateOne(session, eq("released", released), unset("field"));    
        }
        
    }
}
        """,
    )
    fun `supports updateOne calls with a session`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter =
            parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdates =
            parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasUpdates.children[0]
        assertEquals(Name.UNSET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateOne(eq("released", released), unset("field"));
    }
}
        """,
    )
    fun `supports updateOne calls with a filter and update expressions`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdate = parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasUpdate.children[0]
        assertEquals(Name.UNSET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateOne(eq("released", released), (unset("field")));
    }
}
        """,
    )
    fun `supports updateOne calls with a filter and update expressions in parenthesis`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdate = parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasUpdate.children[0]
        assertEquals(Name.UNSET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> updateReleasedBooks() {
        return updateManyByReleasedFlag(true);
    }
    
    private Document updateManyByReleasedFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateMany(eq("released", released), set("field", 1));
    }
}
        """,
    )
    fun `supports updateMany calls with a filter and update expressions setting a value`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "updateReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdates = parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasUpdates.children[0]
        assertEquals(Name.SET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            1,
            (unset.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> updateReleasedBooks() {
        return updateManyByReleasedFlag(true);
    }
    
    private Document updateManyByReleasedFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateMany(eq("released", released), combine(set("field", 1), unset("anotherField")));
    }
}
        """,
    )
    fun `supports updates combining update operations`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "updateReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdate = parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val combine = hasUpdate.children[0]
        assertEquals(Name.COMBINE, combine.component<Named>()!!.name)
        assertEquals(2, combine.component<HasUpdates<Unit?>>()!!.children.size)

        val updates = combine.component<HasUpdates<Unit?>>()!!.children
        assertEquals(Name.SET, updates[0].component<Named>()!!.name)
        assertEquals(Name.UNSET, updates[1].component<Named>()!!.name)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        try (var session = client.startSession()) {
            return client.getDatabase("myDatabase")
                    .getCollection("myCollection")
                    .updateOne(eq("released", released), addEachToSet("field", "value"));  
        }
        
    }
}
        """,
    )
    fun `supports parsing unidentified update method calls as UNKNOWN`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter =
            parsedQuery.component<HasFilter<Unit?>>()!!
        val hasUpdates =
            parsedQuery.component<HasUpdates<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        // It is unknown for now but it might change when we start supporting that update
        // operator. In that case the test should be updated as well.
        val addEachToSet = hasUpdates.children[0]
        assertEquals(Name.UNKNOWN, addEachToSet.component<Named>()!!.name)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(String[] validGenres) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", validGenres));
    }
}
        """,
    )
    fun `supports the in operator as array`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(String genre) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", genre));
    }
}
        """,
    )
    fun `supports the in operator with a single element`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(List<String> genres) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", genres));
    }
}
        """,
    )
    fun `supports the in operator with a list`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findFantasyBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", "Fantasy"));
    }
}
        """,
    )
    fun `supports the in operator with a list and detects constant values`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findFantasyBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            listOf("Fantasy"),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findFantasyBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", "Fantasy", "Sci-Fi"));
    }
}
        """,
    )
    fun `supports the in operator with a list and detects constant values on varargs`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findFantasyBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            listOf("Fantasy", "Sci-Fi"),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findFantasyBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", "Fantasy", 50));
    }
}
        """,
    )
    fun `supports the in operator with a list and detects constant values on varargs and infers types`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findFantasyBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString, BsonInt32)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            listOf("Fantasy", 50),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findFantasyBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(exists("genre"));
    }
}
        """,
    )
    fun `supports the exists operator with single param call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findFantasyBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EXISTS, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Inferred).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Inferred).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findFantasyBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(exists("genre", false));
    }
}
        """,
    )
    fun `supports the exists operator with double param call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findFantasyBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasFilter = parsedQuery.component<HasFilter<Unit?>>()!!

        val eq = hasFilter.children[0]
        assertEquals(Name.EXISTS, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.FromSchema).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Object randomQuery() {
        return this.collection."|"();
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "countDocuments;;COUNT_DOCUMENTS",
            "estimatedDocumentCount;;ESTIMATED_DOCUMENT_COUNT",
            "distinct;;DISTINCT",
            "find;;FIND_MANY",
            "aggregate;;AGGREGATE",
            "insertOne;;INSERT_ONE",
            "insertMany;;INSERT_MANY",
            "deleteOne;;DELETE_ONE",
            "deleteMany;;DELETE_MANY",
            "replaceOne;;REPLACE_ONE",
            "updateOne;;UPDATE_ONE",
            "updateMany;;UPDATE_MANY",
            "findOneAndDelete;;FIND_ONE_AND_DELETE",
            "findOneAndUpdate;;FIND_ONE_AND_UPDATE",
            "createIndex;;UNKNOWN",
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
            val parsedQuery = JavaDriverDialect.parser.parse(query)

            val knownReference = parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Known
            val command = parsedQuery.component<IsCommand>()
            val namespace = knownReference.namespace

            assertEquals("simple", namespace.database)
            assertEquals("books", namespace.collection)
            assertEquals(expected, command?.type)
        }
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public MongoCursor<Document> findBookById(ObjectId id) {
        return getCollection().find(eq("_id", id)).iterator();
    }
    
    private MongoCollection<Document> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `correctly parses a find query with iterator() as FIND_MANY command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        // Although the entire query ends with .iterator, that is not our actual query candidate
        // for parsing. The actual query is right before .iterator. IntelliJ triggers a parse call
        // for that expression as well, so we are simulating the same here by manually grabbing the
        // correct expression.
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("id))") }
        val parsedQuery = JavaDriverDialect.parser.parse(actualQuery)
        val command = parsedQuery.component<IsCommand>()
        assertEquals(IsCommand.CommandType.FIND_MANY, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;
public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public MongoCursor<Document> findBookById(ObjectId id) {
        return getCollection().find(eq("_id", id)).cursor();
    }
    
    private MongoCollection<Document> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `correctly parses a find query with cursor() as FIND_MANY command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        // Although the entire query ends with .cursor, that is not our actual query candidate
        // for parsing. The actual query is right before .cursor. IntelliJ triggers a parse call
        // for that expression as well, so we are simulating the same here by manually grabbing the
        // correct expression.
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("id))") }
        val parsedQuery = JavaDriverDialect.parser.parse(actualQuery)
        val command = parsedQuery.component<IsCommand>()
        assertEquals(IsCommand.CommandType.FIND_MANY, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public MongoCursor<Document> findBookById(ObjectId id) {
        return getCollection().aggregate(List.of()).iterator();
    }
    
    private MongoCollection<Document> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `correctly parses an aggregate with iterator() as AGGREGATE command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        // Although the entire agg ends with .iterator, that is not our actual query candidate
        // for parsing. The actual query is right before .iterator. IntelliJ triggers a parse call
        // for that expression as well, so we are simulating the same here by manually grabbing the
        // correct expression.
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedQuery = JavaDriverDialect.parser.parse(actualQuery)
        val command = parsedQuery.component<IsCommand>()
        assertEquals(IsCommand.CommandType.AGGREGATE, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public MongoCursor<Document> findBookById(ObjectId id) {
        return getCollection().aggregate(List.of()).cursor();
    }
    
    private MongoCollection<Document> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `correctly parses an aggregate with cursor() as AGGREGATE command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        // Although the entire agg ends with .cursor, that is not our actual query candidate
        // for parsing. The actual query is right before .cursor. IntelliJ triggers a parse call
        // for that expression as well, so we are simulating the same here by manually grabbing the
        // correct expression.
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedQuery = JavaDriverDialect.parser.parse(actualQuery)
        val command = parsedQuery.component<IsCommand>()
        assertEquals(IsCommand.CommandType.AGGREGATE, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `correctly parses FindIterable#first as FIND_ONE command`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)
        val command = parsedQuery.component<IsCommand>()
        assertEquals(IsCommand.CommandType.FIND_ONE, command?.type)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).sort(Sorts.ascending("myField")).first();
    }
}
        """,
    )
    fun `correctly parses FindIterable#sort as a SORT component`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)
        val sorting = parsedQuery.component<HasSorts<PsiElement>>()!!
        val sortByMyFieldName = sorting.children[0].component<HasFieldReference<PsiElement>>()!!.reference
        val sortByMyFieldOrder = sorting.children[0].component<HasValueReference<PsiElement>>()!!.reference

        assertEquals("myField", (sortByMyFieldName as HasFieldReference.FromSchema).fieldName)
        assertEquals(1, (sortByMyFieldOrder as HasValueReference.Inferred).value)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).sort(Sorts.ascending("myField")).limit(22).first();
    }
}
        """,
    )
    fun `supports parsing limit chained to a find query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)
        val limit = parsedQuery.component<HasLimit>()

        assertEquals(22, limit?.limit)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        int limitVal = 22;
        return this.collection.find(eq("_id", id)).sort(Sorts.ascending("myField")).limit(limitVal).first();
    }
}
        """,
    )
    fun `supports parsing limit chained to a find query as a variable`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)
        val limit = parsedQuery.component<HasLimit>()

        assertEquals(22, limit?.limit)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;import org.bson.types.ObjectId;
import java.util.ArrayList;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    int getLimit() {
        return 22;
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).sort(Sorts.ascending("myField")).limit(getLimit()).first();
    }
}
        """,
    )
    fun `supports parsing limit chained to a find query as a method call`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)
        val limit = parsedQuery.component<HasLimit>()

        assertEquals(22, limit?.limit)
    }
}

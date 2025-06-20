package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@IntegrationTest
class NamespaceExtractorTest {
    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    private final MongoDatabase database = client.getDatabase("db");
    private final MongoCollection<Document> collection = database.getCollection("coll");
    
    public Object queryMethod(ObjectId id) {
        return collection.find();
    }
}
      """
    )
    fun `extracts namespace from database and collection property declared and defined in class`(psiFile: PsiFile) {
        val bookQuery = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            bookQuery
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    private final MongoDatabase database = client.getDatabase("db");
    private final MongoCollection<Document> collection;
    
    public Object queryMethod(ObjectId id) {
        this.collection = this.database.getCollection("coll");
        return this.collection.find();
    }
}
      """
    )
    fun `extracts namespace from database declared and defined in class and collection declared in class and defined in queryMethod`(psiFile: PsiFile) {
        val bookQuery = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            bookQuery
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;
    
    public Object queryMethod(ObjectId id) {
        this.database = this.client.getDatabase("db");
        this.collection = this.database.getCollection("coll");
        return this.collection.find();
    }
}
      """
    )
    fun `extracts namespace from database and collection declared in class and defined in queryMethod`(psiFile: PsiFile) {
        val bookQuery = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            bookQuery
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public MongoCollection<Document> getCollection() {
        return this.client.getDatabase("db").getCollection("coll");
    }
    
    public Object queryMethod(ObjectId id) {
        return getCollection().find();
    }
}
      """
    )
    fun `extracts namespace from a method call when method has entire namespace definition`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            query
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    private final MongoDatabase database = client.getDatabase("db");
    
    public MongoCollection<Document> getCollection() {
        return database.getCollection("coll");
    }
    
    public Object queryMethod(ObjectId id) {
        return getCollection().find();
    }
}
      """
    )
    fun `extracts collection independently of database defined as class property from a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            query
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    private MongoDatabase getDatabase() {
        return this.client.getDatabase("db");
    }
    
    public MongoCollection<Document> getCollection() {
        return getDatabase().getCollection("coll");
    }
    
    public Object queryMethod(ObjectId id) {
        return getCollection().find();
    }
}
      """
    )
    fun `extracts collection (from a method call) independently of database retrieved from a method call`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            query
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(collReference)
        assertEquals("db", collReference.namespace.database)
        assertEquals("coll", collReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public MongoCollection<Document> getCollection(MongoDatabase db) {
        return db.getCollection("coll");
    }
    
    public Object queryMethod(ObjectId id) {
        MongoDatabase db = this.client.getDatabase("db2");
        return getCollection(db).find();
    }
}
      """
    )
    fun `extracts collection (from a method call) independently of database provided as method arguments but fails for database`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "queryMethod")
        val collReference = NamespaceExtractor.extractNamespace(
            query
        ).reference as HasCollectionReference.OnlyCollection<*>
        assertEquals("coll", collReference.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public Document getBooks(ObjectId id) {
        return client.getDatabase("simple").getCollection("books").find(eq("_id", id)).first();
    }
    
    public Document getMovies(ObjectId id) {
        return client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
    }
}
      """
    )
    fun `extracts different namespaces from different queries in different methods`(psiFile: PsiFile) {
        val bookQuery = psiFile.getQueryAtMethod("Repository", "getBooks")
        val bookColReference = NamespaceExtractor.extractNamespace(
            bookQuery
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(bookColReference)
        assertEquals("simple", bookColReference.namespace.database)
        assertEquals("books", bookColReference.namespace.collection)

        val movieQuery = psiFile.getQueryAtMethod("Repository", "getMovies")
        val movieColReference = NamespaceExtractor.extractNamespace(
            movieQuery
        ).reference as HasCollectionReference.Known<*>
        assertNotNull(movieColReference)
        assertEquals("sample_mflix", movieColReference.namespace.database)
        assertEquals("movies", movieColReference.namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    // Two simple queries in the same method
    public Document getBooks(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return client.getDatabase("simple").getCollection("books").find(eq("_id", id)).first();
    }
    
    // Two queries in the same method where one is getting both database and collection from a variable
    public Document getBooks1(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        var database = client.getDatabase("simple");
        var collection = database.getCollection("books");
        return collection.find(eq("_id", id)).first();
    }
    
    MongoDatabase db2 = client.getDatabase("simple");
    MongoCollection<Document> coll2 = db2.getCollection("books");
    
    
    // Two queries in the same method where one is getting both database and collection from a field
    public Document getBooks2(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return coll2.find(eq("_id", id)).first();
    }
    
    MongoDatabase getDatabase() {
        return client.getDatabase("simple");
    }
    
    MongoCollection<Document> getCollection() {
        return getDatabase().getCollection("books");
    }
    
    // Two queries in the same method where one is getting both database and collection from a factory method
    public Document getBooks3(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return getCollection().find(eq("_id", id)).first();
    }
}
      """
    )
    fun `extracts correct namespace even when there are multiple queries in the same method`(psiFile: PsiFile) {
        val bookQueries = listOf(
            psiFile.getQueryAtMethod("Repository", "getBooks"),
            psiFile.getQueryAtMethod("Repository", "getBooks1"),
            psiFile.getQueryAtMethod("Repository", "getBooks2"),
            psiFile.getQueryAtMethod("Repository", "getBooks3"),
        )

        for (bookQuery in bookQueries) {
            val bookColReference = NamespaceExtractor.extractNamespace(
                bookQuery
            ).reference as HasCollectionReference.Known<*>
            assertNotNull(bookColReference)
            assertEquals("simple", bookColReference.namespace.database)
            assertEquals("books", bookColReference.namespace.collection)
        }
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoDatabase;import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    private final MongoDatabase database;
    
    public Repository(MongoClient client, MongoDatabase database) {
        this.client = client;
        this.database = database;
    }
    
    // Two simple queries in the same method
    public Document getBooks(MongoDatabase db, ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return db.getCollection("books").find(eq("_id", id)).first();
    }
    
    // Two queries in the same method where one is getting a collection from a variable
    public Document getBooks1(MongoDatabase db, ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        var collection = db.getCollection("books");
        return collection.find(eq("_id", id)).first();
    }
    
    MongoCollection<Document> coll2 = database.getCollection("books");
    
    
    // Two queries in the same method where one is getting a collection from a field
    public Document getBooks2(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return coll2.find(eq("_id", id)).first();
    }
    
    MongoCollection<Document> getCollection() {
        return database.getCollection("books");
    }
    
    // Two queries in the same method where one is getting a collection from a factory method
    public Document getBooks3(ObjectId id) {
        var movies = client.getDatabase("sample_mflix").getCollection("movies").find(eq("_id", id)).first();
        return getCollection().find(eq("_id", id)).first();
    }
}
      """
    )
    fun `extracts correct namespace (OnlyCollection) even when there are multiple queries in the same method`(psiFile: PsiFile) {
        val bookQueries = listOf(
            psiFile.getQueryAtMethod("Repository", "getBooks"),
            psiFile.getQueryAtMethod("Repository", "getBooks1"),
            psiFile.getQueryAtMethod("Repository", "getBooks2"),
            psiFile.getQueryAtMethod("Repository", "getBooks3"),
        )

        for (bookQuery in bookQueries) {
            val bookColReference = NamespaceExtractor.extractNamespace(
                bookQuery
            ).reference as HasCollectionReference.OnlyCollection<*>
            assertNotNull(bookColReference)
            assertEquals("books", bookColReference.collection)
        }
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoCollection<T> collection) {
        this.collection = collection;
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class UserRepository extends AbstractRepository<User> {
    public UserRepository(MongoClient client) {
        super(client.getDatabase("production").getCollection("users", User.class));
    }
    
    public User findUserById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("production", namespace.database)
        assertEquals("users", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoCollection<T> collection) {
        this.collection = collection;
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class UserRepository extends AbstractRepository<User> {
    public UserRepository(MongoClient client) {
        super(client.getDatabase("production").getCollection("users", User.class));
    }
    
    public User findUserById(ObjectId id) {
        return findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection without explicit super call`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("production", namespace.database)
        assertEquals("users", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoClient client, String database, String collection) {
        this.collection = client.getDatabase(database).getCollection(collection);
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class BookRepository extends AbstractRepository<Book> {
    public BookRepository(MongoClient client) {
        super(client, "staging", "books");
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with different arguments`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("staging", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import java.lang.String;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoClient client, String database, String collection) {
        this.collection = client.getDatabase(database).getCollection(collection);
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class BookRepository extends AbstractRepository<Book> {
    private static final String DATABASE = "staging";
    private static final String COLLECTION = "books";
    
    public BookRepository(MongoClient client) {
        super(client, DATABASE, COLLECTION);
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with java constants`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("staging", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoClient<T> client;
    private final String database;
    private final String collection;
    
    protected AbstractRepository(MongoClient client, String database, String collection) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }
    
    protected final T findById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
    
    protected final MongoCollection<T> getCollection() {
        return this.client.getDatabase(database).getCollection(collection);
    }
}

public final class BookRepository extends AbstractRepository<Book> {
    public BookRepository(MongoClient client) {
        super(client, "production", "books");
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with a factory method`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("production", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoCollection<Book> collection;
    
    public BookRepository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public User findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoClient client;
    
    public BookRepository(MongoClient client) {
        this.client = client;
    }
    
    public User findBookById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
    
    private MongoCollection<Book> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection and a factory method`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoClient client;
    
    public BookRepository(MongoClient client) {
        this.client = client;
    }
    
    public User findBookById(ObjectId id) {
        return client.getDatabase("simple").getCollection("books").find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection only`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class JavaDriverRepository {
    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
}
        """,
    )
    fun `extracts from a hardcoded example`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("JavaDriverRepository", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class BaseRepository {
    private final MongoClient client;
    private final String database;
    private final String collection;

    protected BaseRepository(MongoClient client, String database, String collection) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }

    protected final MongoCollection<Document> getCollection() {
        return client.getDatabase(database).getCollection(collection);
    }
}

public class JavaDriverRepository extends BaseRepository {
    public static final String DATABASE = "myDatabase";
    public static final String COLLECTION = "myCollection";

    public JavaDriverRepository(MongoClient client) {
        super(client, DATABASE, COLLECTION);
    }

    public FindIterable<Document> exampleFind() {
        return getCollection().find();
    }
}
        """,
    )
    fun `extracts from a mms like example`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("JavaDriverRepository", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class AbstractBaseRepository {
    private final MongoClient client;
    private final String database;
    private final String collection;

    protected AbstractBaseRepository(MongoClient client, String database, String collection) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }

    protected final MongoCollection<Document> getCollection() {
        return client.getDatabase(database).getCollection(collection);
    }
}

abstract class BaseAuthBaseRepository extends AbstractBaseRepository {
    protected BaseAuthBaseRepository(MongoClient client, String database, String collection) {
        super(client, database, collection);
    }
}

public class JavaDriverRepository extends BaseAuthBaseRepository {
    public static final String DATABASE = "myDatabase";
    public static final String COLLECTION = "myCollection";

    public JavaDriverRepository(MongoClient client) {
        super(client, DATABASE, COLLECTION);
    }

    public FindIterable<Document> exampleFind() {
        return getCollection().find();
    }
}
        """,
    )
    fun `extracts from a mms like example with multiple super classes`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("JavaDriverRepository", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class Actor {
    public static final String DB_NAME = "myDatabase";
    public static final String COLLECTION_NAME = "myCollection";
}

abstract class BaseDao<T> {
    private final MongoClient client;
    private final String database;
    private final String collection;

    protected BaseDao(MongoClient client, String database, String collection) {
        this(client, database, collection, true);
    }
    
     protected BaseDao(MongoClient client, String database, String collection, boolean unused1) {
        this(client, database, collection, unused1, true);
    }
    
    protected BaseDao(MongoClient client, String database, String collection, boolean unused1, boolean unused2) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }

    protected MongoDatabase getDatabase(MongoClient client, String databaseName) {
        return client.getDatabase(databaseName);
    }
    
    protected final MongoCollection<T> getCollection() {
        return getDatabase(client, database)
               .getCollection(collection);
    }
}

public class ActorDao extends BaseDao<Actor> {
    public ActorDao(MongoClient client) {
        super(client, Actor.DB_NAME, Actor.COLLECTION_NAME);
    }

    public FindIterable<Document> exampleFind() {
        return getCollection().find();
    }
}
        """,
    )
    fun `extracts from a mms like example with multiple super classes and references to this`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("ActorDao", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

class BaseDao<Entity> {
  protected MongoCollection<Entity> collection;

  BaseDao(MongoClient client, Class<Entity> entityClass, String dbName, String collectionName) {
    this.collection = client.getDatabase(dbName).getCollection(collectionName).withDocumentClass(entityClass);
  }

  public Optional<Entity> findById(String id) {
    return Optional.ofNullable(collection.find(Filters.eq("_id", id)).first());
  }
}

class ActorDao extends BaseDao<Actor> {
  static final String DB = "myDatabase";
  static final String COLLECTION = "myCollection";

  public ActorDao(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, DB, COLLECTION);
  }
  
  public Optional<Actor> getById(String id) {
    return findById(id);  
  }
}

class Actor {
  @BsonId
  public String id;

  @BsonProperty
  public List<Document> policyAssignments;
}
        """,
    )
    fun `inheritance with document class and generics`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("ActorDao", "getById")
        val namespace = (
            NamespaceExtractor.extractNamespace(methodToAnalyse).reference
                as HasCollectionReference.Known<PsiElement>
            ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.ReadConcern;import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import com.mongodb.client.model.Filters;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

class DaoConstants {
    public static final String DB_NAME = "myDatabase";
}

class MongoClientContainer {
    public MongoClient getClient() {
        return null;
    }
}

class BaseDao<Entity> {
  private final MongoClientContainer clientContainer;
  protected final String dbName;
  protected final String collName;
  private final Class<Entity> docClass;
  
  protected BaseDao(MongoClientContainer clientContainer, Class<Entity> entityClass, String dbName, String collectionName) {
    this(clientContainer, entityClass, dbName, collectionName, false);
  }
  
  protected BaseDao(MongoClientContainer clientContainer, Class<Entity> entityClass, String dbName, String collectionName, boolean unused) {
    this.clientContainer = clientContainer;
    this.dbName = dbName;
    this.collName = collectionName;
    this.docClass = entityClass;
  }
  
  protected MongoClient getClient() {
    return clientContainer.getClient();
  }
  
  protected MongoCollection<T> getCollection() {
    return getDatabase(getClient(), dbName)
            .getCollection(collName, docClass)
            .withCodecRegistry(CodecRegistries.fromRegistries());
  }
  
  protected MongoDatabase getDatabase(MongoClient client, String databaseName) {
    return client.getDatabase(databaseName);
  }
}

class ActorDao extends BaseDao<Actor> {
  public ActorDao(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, Actor.DB, Actor.COLLECTION);
  }
  
  public Optional<Actor> findById(String id) {
    return Optional.ofNullable(getCollection().find(Filters.eq("_id", id)).first()); 
  }
  
  @Override
  protected MongoCollection<Actor> getCollection() {
      return super.getCollection()
                .withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY);
  }
}

class AnotherDao extends BaseDao<Another> {
  static final String DB = "myAnotherDatabase";
  static final String COLLECTION = "myAnotherCollection";

  public AnotherDao(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, DB, COLLECTION);
  }
}

class AnotherDao2 extends BaseDao<Another> {
  static final String DB = "myAnotherDatabase";
  static final String COLLECTION = "myAnotherCollection";

  public AnotherDao2(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, DB, COLLECTION);
  }
}

class AnotherDao3 extends BaseDao<Another> {
  public AnotherDao3(MongoClient client) {
    super(client, Actor.class, "myJsonDb", "myJsonColl");
  }
}

class Actor {
   public static final String DB = DaoConstants.DB_NAME;
   public static final String COLLECTION = "myCollection";
     
  @BsonId
  public String id;

  @BsonProperty
  public List<Document> policyAssignments;
}

class Another {
  @BsonId
  public String id;

  @BsonProperty
  public List<Document> policyAssignments;
}
        """,
    )
    fun `inheritance with document class and generics and overridden methods`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("ActorDao", "findById")
        val namespace = (
            NamespaceExtractor.extractNamespace(methodToAnalyse).reference
                as HasCollectionReference.Known<PsiElement>
            ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.ReadConcern;import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;import com.mongodb.client.model.Filters;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

class DaoConstants {
    public static final String DB_NAME = "myDatabase";
}

class MongoClientContainer {
    public MongoClient getClient() {
        return null;
    }
}

interface BaseDaoInterface<Entity> { 
  MongoClient getClient();
  MongoCollection<T> getCollection();
}

class BaseDao<Entity> implements BaseDaoInterface<Entity> {
  private final MongoClientContainer clientContainer;
  protected final String dbName;
  protected final String collName;
  private final Class<Entity> docClass;
  
  protected BaseDao(MongoClientContainer clientContainer, Class<Entity> entityClass, String dbName, String collectionName) {
    this(clientContainer, entityClass, dbName, collectionName, false);
  }
  
  protected BaseDao(MongoClientContainer clientContainer, Class<Entity> entityClass, String dbName, String collectionName, boolean unused) {
    this.clientContainer = clientContainer;
    this.dbName = dbName;
    this.collName = collectionName;
    this.docClass = entityClass;
  }
  
  protected MongoClient getClient() {
    return clientContainer.getClient();
  }
  
  protected MongoCollection<T> getCollection() {
    return getDatabase(getClient(), dbName)
            .getCollection(collName, docClass)
            .withCodecRegistry(CodecRegistries.fromRegistries());
  }
  
  protected MongoDatabase getDatabase(MongoClient client, String databaseName) {
    return client.getDatabase(databaseName);
  }
}

class ActorDao extends BaseDao<Actor>, implements BaseDaoInterface<Actor> {
  public ActorDao(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, Actor.DB, Actor.COLLECTION);
  }
  
  public Optional<Actor> findById(String id) {
    return Optional.ofNullable(getCollection().find(Filters.eq("_id", id)).first()); 
  }
  
  @Override
  protected MongoCollection<Actor> getCollection() {
      return super.getCollection()
                .withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY);
  }
}

class AnotherDao extends BaseDao<Another>, implements BaseDaoInterface<Actor> {
  static final String DB = "myAnotherDatabase";
  static final String COLLECTION = "myAnotherCollection";

  public AnotherDao(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, DB, COLLECTION);
  }
}

class AnotherDao2 extends BaseDao<Another>, implements BaseDaoInterface<Actor> {
  static final String DB = "myAnotherDatabase";
  static final String COLLECTION = "myAnotherCollection";

  public AnotherDao2(MongoClient client, String dbName, String collectionName) {
    super(client, Actor.class, DB, COLLECTION);
  }
}

class AnotherDao3 extends BaseDao<Another> {
  public AnotherDao3(MongoClient client) {
    super(client, Actor.class, "myJsonDb", "myJsonColl");
  }
}

class Actor {
   public static final String DB = DaoConstants.DB_NAME;
   public static final String COLLECTION = "myCollection";
     
  @BsonId
  public String id;

  @BsonProperty
  public List<Document> policyAssignments;
}

class Another {
  @BsonId
  public String id;

  @BsonProperty
  public List<Document> policyAssignments;
}
        """,
    )
    fun `inheritance with document class and generics with interfaces and overridden methods`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("ActorDao", "findById")
        val namespace = (
            NamespaceExtractor.extractNamespace(methodToAnalyse).reference
                as HasCollectionReference.Known<PsiElement>
            ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }
}

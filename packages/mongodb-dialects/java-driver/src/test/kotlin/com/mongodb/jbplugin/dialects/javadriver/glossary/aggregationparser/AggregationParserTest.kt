package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectParser
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@IntegrationTest
class AggregationParserTest {

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
      """
    )
    fun `should identify an aggregation as a valid candidate for parsing`(psiFile: PsiFile) {
        val psiManager = PsiManager.getInstance(psiFile.project)
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        // The entire aggregation is not a valid candidate
        assertFalse(
            psiManager.areElementsEquivalent(
                query,
                JavaDriverDialectParser.attachment(query)
            )
        )

        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        // Only the collection call is the valid query
        assertTrue(
            psiManager.areElementsEquivalent(
                query,
                JavaDriverDialectParser.attachment(actualQuery)
            )
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
      """
    )
    fun `should consider the MongoCollection#aggregate as the attachment for query`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }

        assertEquals(actualQuery, JavaDriverDialectParser.attachment(actualQuery))
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
        """,
    )
    fun `can extract the namespace of an aggregate`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)

        val knownReference = parsedAggregate.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Known
        val command = parsedAggregate.component<IsCommand>()
        val dialect = parsedAggregate.component<HasSourceDialect>()
        val namespace = knownReference.namespace

        assertEquals(HasSourceDialect.DialectName.JAVA_DRIVER, dialect?.name)
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
        assertEquals(IsCommand.CommandType.AGGREGATE, command?.type)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
        """,
    )
    fun `should be able to parse an empty aggregation built with List#of method`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()

        assertNotNull(hasAggregation)
        assertEquals(hasAggregation?.children?.isEmpty(), true)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(Arrays.asList()).first();
    }
}
        """,
    )
    fun `should be able to parse an empty aggregation built with Arrays#asList method`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("asList())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()

        assertNotNull(hasAggregation)
        assertEquals(hasAggregation?.children?.isEmpty(), true)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchPath;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> findBookById(ObjectId id) {
        // The following list of aggregation stages was created using the api doc for 5.5 driver.
        // https://mongodb.github.io/mongo-java-driver/5.5/apidocs/driver-core/com/mongodb/client/model/Aggregates
        // These are all the aggregation stages that we do not yet support.
        return this.collection.aggregate(List.of(
            Aggregates.bucket(),
            Aggregates.bucketAuto(),
            Aggregates.count(),
            Aggregates.densify(),
            Aggregates.documents(),
            Aggregates.facet(),
            Aggregates.fill(),
            Aggregates.geoNear(),
            Aggregates.graphLookup(),
            Aggregates.limit(),
            Aggregates.lookup(),
            Aggregates.merge(),
            Aggregates.out(),
            Aggregates.replaceRoot(),
            Aggregates.sample(),
            Aggregates.search(),
            Aggregates.searchMeta(),
            Aggregates.set(),
            Aggregates.setWindowFields(),
            Aggregates.skip(),
            Aggregates.sortByCount(),
            Aggregates.unionWith(),
            Aggregates.unset(),
            Aggregates.vectorSearch()
        ));
    }
}
      """
    )
    fun `should parse unidentified stages as UNKNOWN stage nodes`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(query)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()

        assertNotNull(hasAggregation)
        for (stage in hasAggregation!!.children) {
            assertEquals(Name.UNKNOWN, stage.component<Named>()?.name)
        }
    }
}

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonEnum
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private typealias PsiTypeProvider = (Project) -> PsiType

@IntegrationTest
class PsiMdbTreeUtilTest {
    @ParameterizedTest
    @MethodSource("psiTypeToBsonType")
    fun `should map all psi types to their corresponding bson types`(
        typeProvider: PsiTypeProvider,
        expected: BsonType,
        project: Project,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val psiType = typeProvider(project)
            assertEquals(expected, psiType.toBsonType())
        }
    }

    @ParameterizedTest
    @MethodSource("stringToBsonType")
    fun `should map all known java qualified names to their corresponding bson types`(
        javaQualifiedName: String,
        expected: BsonType,
    ) {
        assertEquals(expected, javaQualifiedName.toBsonType())
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;

public final class Repository {
    public enum Language {
        CATALAN,
        ENGLISH,
        FRENCH,
        HINDI,
        SPANISH
    }

    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public List<Document> findBooksInCatalan() {
        return this.collection.find(Filters.eq("language", Language.CATALAN));
    }
}
      """
    )
    fun `should understand a java enum as a constant value`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksInCatalan")
        val parsedQuery = JavaDriverDialectParser.parse(query)
        val filter = parsedQuery.component<HasFilter<PsiElement>>()!!
        val eqOp = filter.children[0]
        val value = eqOp.component<HasValueReference<PsiElement>>()!!
        val valueRef = value.reference as HasValueReference.Constant<PsiElement>
        val valueType = valueRef.type

        assertEquals(
            BsonEnum(setOf("CATALAN", "ENGLISH", "FRENCH", "HINDI", "SPANISH"), "Language"),
            valueType
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;

public final class Repository {
    public enum Language {
        CATALAN,
        ENGLISH,
        FRENCH,
        HINDI,
        SPANISH
    }

    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public List<Document> findBooksInCatalan(Language lang) {
        return this.collection.find(Filters.eq("language", lang));
    }
}
      """
    )
    fun `should understand a java enum as a runtime value`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksInCatalan")
        val parsedQuery = JavaDriverDialectParser.parse(query)
        val filter = parsedQuery.component<HasFilter<PsiElement>>()!!
        val eqOp = filter.children[0]
        val value = eqOp.component<HasValueReference<PsiElement>>()!!
        val valueRef = value.reference as HasValueReference.Runtime<PsiElement>
        val valueType = valueRef.type

        assertEquals(
            BsonEnum(setOf("CATALAN", "ENGLISH", "FRENCH", "HINDI", "SPANISH"), "Language"),
            valueType
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;

public final class Repository {
    private static final String LANGUAGE = "language";
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public List<Document> findBooksInCatalan() {
        return this.collection.find(Filters.eq("$" + LANGUAGE, "Catalan"));
    }
}
      """
    )
    fun `should understand a java constant concatenation with strings`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksInCatalan")
        val parsedQuery = JavaDriverDialectParser.parse(query)
        val filter = parsedQuery.component<HasFilter<PsiElement>>()!!
        val eqOp = filter.children[0]
        val field = eqOp.component<HasFieldReference<PsiElement>>()!!
        val fieldRef = field.reference as HasFieldReference.FromSchema<PsiElement>
        val fieldName = fieldRef.fieldName

        assertEquals("${'$'}language", fieldName)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;

public final class Repository {
    private static final String LANGUAGE = "language";
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public List<Document> findBooksInCatalan() {
        return this.collection.find(Filters.eq("$" + "language", "Catalan"));
    }
}
      """
    )
    fun `should understand a java literal string concatenation`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksInCatalan")
        val parsedQuery = JavaDriverDialectParser.parse(query)
        val filter = parsedQuery.component<HasFilter<PsiElement>>()!!
        val eqOp = filter.children[0]
        val field = eqOp.component<HasFieldReference<PsiElement>>()!!
        val fieldRef = field.reference as HasFieldReference.FromSchema<PsiElement>
        val fieldName = fieldRef.fieldName

        assertEquals("${'$'}language", fieldName)
    }

    companion object {
        @JvmStatic
        fun psiTypeToBsonType(): Array<Array<Any>> =
            arrayOf(
                arrayOf(
                    { project: Project -> project.findClass("org.bson.types.ObjectId") },
                    BsonAnyOf(BsonObjectId, BsonNull),
                ),
                arrayOf(
                    { _: Project -> PsiTypes.booleanType() },
                    BsonBoolean,
                ),
                arrayOf(
                    { _: Project -> PsiTypes.shortType() },
                    BsonInt32,
                ),
                arrayOf(
                    { _: Project -> PsiTypes.intType() },
                    BsonInt32,
                ),
                arrayOf(
                    { _: Project -> PsiTypes.longType() },
                    BsonInt64,
                ),
                arrayOf(
                    { _: Project -> PsiTypes.floatType() },
                    BsonDouble,
                ),
                arrayOf(
                    { _: Project -> PsiTypes.doubleType() },
                    BsonDouble,
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.lang.CharSequence") },
                    BsonAnyOf(BsonString, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.lang.String") },
                    BsonAnyOf(BsonString, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.util.Date") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.time.LocalDate") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.time.LocalDateTime") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.math.BigInteger") },
                    BsonAnyOf(BsonInt64, BsonNull),
                ),
                arrayOf(
                    { project: Project -> project.findClass("java.math.BigDecimal") },
                    BsonAnyOf(BsonDecimal128, BsonNull),
                ),
            )

        @JvmStatic
        fun stringToBsonType(): Array<Array<Any>> = arrayOf(
            arrayOf("org.bson.types.ObjectId", BsonAnyOf(BsonObjectId, BsonNull)),
            arrayOf("boolean", BsonBoolean),
            arrayOf("java.lang.Boolean", BsonBoolean),
            arrayOf("short", BsonInt32),
            arrayOf("java.lang.Short", BsonInt32),
            arrayOf("int", BsonInt32),
            arrayOf("java.lang.Integer", BsonInt32),
            arrayOf("long", BsonInt64),
            arrayOf("java.lang.Long", BsonInt64),
            arrayOf("float", BsonDouble),
            arrayOf("java.lang.Float", BsonDouble),
            arrayOf("double", BsonDouble),
            arrayOf("java.lang.Double", BsonDouble),
            arrayOf("java.lang.CharSequence", BsonAnyOf(BsonString, BsonNull)),
            arrayOf("java.lang.String", BsonAnyOf(BsonString, BsonNull)),
            arrayOf("String", BsonAnyOf(BsonString, BsonNull)),
            arrayOf("java.util.Date", BsonAnyOf(BsonDate, BsonNull)),
            arrayOf("java.time.LocalDate", BsonAnyOf(BsonDate, BsonNull)),
            arrayOf("java.time.LocalDateTime", BsonAnyOf(BsonDate, BsonNull)),
            arrayOf("java.math.BigInteger", BsonAnyOf(BsonInt64, BsonNull)),
            arrayOf("java.math.BigDecimal", BsonAnyOf(BsonDecimal128, BsonNull)),
            arrayOf("int[]", BsonArray(BsonInt32)),
            arrayOf("java.lang.Long[]", BsonArray(BsonInt64)),
            arrayOf("List<String>", BsonArray(BsonAnyOf(BsonString, BsonNull))),
            arrayOf("List<java.lang.Integer>[]", BsonArray(BsonArray(BsonInt32))),
            arrayOf("Set<String>", BsonArray(BsonAnyOf(BsonString, BsonNull))),
            arrayOf("Map<String, Integer>", BsonAny),
            arrayOf("HashMap<String, Integer>", BsonAny),
        )
    }
}

private fun Project.findClass(name: String): PsiType =
    JavaPsiFacade.getElementFactory(this).createTypeByFQClassName(
        name,
    )

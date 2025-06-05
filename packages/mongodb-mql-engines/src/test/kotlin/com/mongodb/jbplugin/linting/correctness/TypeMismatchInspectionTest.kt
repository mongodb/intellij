package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test

class TypeMismatchInspectionTest : QueryInspectionTest<TypeMismatch> {
    @Test
    fun `warn when a field does not match with the schema type`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        onInsight(0).assertInsightDescriptionIs(
            "insight.type-mismatch",
            "myString",
            "BsonString",
            "BsonInt32"
        )
    }

    @Test
    fun `warn when a field does not match with the schema type on multiple conditions`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    ),
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, true, BsonBoolean)
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        onInsight(0).assertInsightDescriptionIs(
            "insight.type-mismatch",
            "myString",
            "BsonString",
            "BsonInt32"
        )

        onInsight(1).assertInsightDescriptionIs(
            "insight.type-mismatch",
            "myString",
            "BsonString",
            "BsonBoolean"
        )
    }

    @Test
    fun `warn when a field does not match with the schema type on multiple fields`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                    "myStrong" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    ),
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myStrong")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, true, BsonBoolean)
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        onInsight(0).assertInsightDescriptionIs(
            "insight.type-mismatch",
            "myString",
            "BsonString",
            "BsonInt32"
        )

        onInsight(1).assertInsightDescriptionIs(
            "insight.type-mismatch",
            "myStrong",
            "BsonString",
            "BsonBoolean"
        )
    }

    @Test
    fun `does not warn if the database does not exist`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database1"))

        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myNonExisting")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    )
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        assertNoInsights()
    }

    @Test
    fun `does not warn if the collection does not exist`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection1"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myNonExisting")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    )
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        assertNoInsights()
    }

    @Test
    fun `does not warn if the field does not exist`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myNonExisting")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    )
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        assertNoInsights()
    }

    @Test
    fun `does not warn on sorts`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))

        whenCollectionSchemaIs(
            collectionNamespace,
            BsonObject(
                mapOf(
                    "myString" to BsonString,
                ),
            )
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            Named(Name.SORT),
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            ),
                            HasValueReference(
                                HasValueReference.Constant(null, 42, BsonInt32)
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = TypeMismatchInspection<Unit>()
        inspection.run(query, holder, TypeMismatchInspectionSettings(Unit, readModelProvider, 50) { it.toString() })

        assertNoInsights()
    }
}

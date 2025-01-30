package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test

class GroupTest {
    @Test
    fun `should emit empty group stage if there is no formattable _id field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {}}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit empty group stage if there is no formattable _id field reference regardless of having accumulated fields`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {}}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasAccumulatedFields(
                                        children = emptyList<Node<Unit>>()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with null _id reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = emptyList<Node<Unit>>()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with _id reference having empty field references`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Computed(
                                            source = Unit,
                                            type = ComputedBsonType(
                                                baseType = BsonAny,
                                                expression = Node(
                                                    source = Unit,
                                                    components = listOf()
                                                )
                                            )
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = emptyList<Node<Unit>>()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with _id reference having just one field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": "${'$'}field0", }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Computed(
                                            source = Unit,
                                            type = ComputedBsonType(
                                                baseType = BsonAny,
                                                expression = Node(
                                                    source = Unit,
                                                    components = listOf(
                                                        HasFieldReference(
                                                            reference = HasFieldReference.FromSchema(
                                                                source = Unit,
                                                                fieldName = "field0",
                                                                displayName = "field0"
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = emptyList<Node<Unit>>()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with _id reference having multiple field references`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": {"field0": "${'$'}field0", "field1": "${'$'}field1"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Computed(
                                            source = Unit,
                                            type = ComputedBsonType(
                                                baseType = BsonAny,
                                                expression = Node(
                                                    source = Unit,
                                                    components = listOf(
                                                        HasFieldReference(
                                                            reference = HasFieldReference.FromSchema(
                                                                source = Unit,
                                                                fieldName = "field0",
                                                                displayName = "field0"
                                                            )
                                                        ),
                                                        HasFieldReference(
                                                            reference = HasFieldReference.FromSchema(
                                                                source = Unit,
                                                                fieldName = "field1",
                                                                displayName = "field1"
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = emptyList<Node<Unit>>()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with sum accumulator with a constant field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "sumField": {"${'$'}sum": 1}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.SUM),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "sumField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Constant(
                                                            source = Unit,
                                                            value = 1,
                                                            type = BsonInt32,
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with sum accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "sumField": {"${'$'}sum": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.SUM),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "sumField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with avg accumulator with a constant field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "avgField": {"${'$'}avg": 1}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.AVG),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "avgField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Constant(
                                                            source = Unit,
                                                            value = 1,
                                                            type = BsonInt32,
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with avg accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "avgField": {"${'$'}avg": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.AVG),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "avgField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with first accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "firstField": {"${'$'}first": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.FIRST),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "firstField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with last accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "lastField": {"${'$'}last": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.LAST),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "lastField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with max accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "maxField": {"${'$'}max": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.MAX),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "maxField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with min accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "minField": {"${'$'}min": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.MIN),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "minField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with push accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "pushField": {"${'$'}push": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.PUSH),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "pushField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with addToSet accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "addToSetField": {"${'$'}addToSet": "${'$'}field0"}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.ADD_TO_SET),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "addToSetField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with top accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "topField": {"${'$'}top": {"sortBy": {"field0": -1, }, "output": "${'$'}field0", }}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.TOP),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "topField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasSorts(
                                                        children = listOf(
                                                            Node(
                                                                source = Unit,
                                                                components = listOf(
                                                                    HasFieldReference(
                                                                        reference = HasFieldReference.FromSchema(
                                                                            source = Unit,
                                                                            fieldName = "field0",
                                                                            displayName = "field0"
                                                                        )
                                                                    ),
                                                                    HasValueReference(
                                                                        reference = HasValueReference.Inferred(
                                                                            source = Unit,
                                                                            value = -1,
                                                                            type = BsonInt32,
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with topN accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "topNField": {"${'$'}topN": {"sortBy": {"field0": -1, }, "output": "${'$'}field0", "n": 3, }}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.TOP_N),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "topNField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasSorts(
                                                        children = listOf(
                                                            Node(
                                                                source = Unit,
                                                                components = listOf(
                                                                    HasFieldReference(
                                                                        reference = HasFieldReference.FromSchema(
                                                                            source = Unit,
                                                                            fieldName = "field0",
                                                                            displayName = "field0"
                                                                        )
                                                                    ),
                                                                    HasValueReference(
                                                                        reference = HasValueReference.Inferred(
                                                                            source = Unit,
                                                                            value = -1,
                                                                            type = BsonInt32,
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasLimit(3)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with bottom accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "bottomField": {"${'$'}bottom": {"sortBy": {"field0": -1, }, "output": "${'$'}field0", }}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.BOTTOM),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "bottomField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasSorts(
                                                        children = listOf(
                                                            Node(
                                                                source = Unit,
                                                                components = listOf(
                                                                    HasFieldReference(
                                                                        reference = HasFieldReference.FromSchema(
                                                                            source = Unit,
                                                                            fieldName = "field0",
                                                                            displayName = "field0"
                                                                        )
                                                                    ),
                                                                    HasValueReference(
                                                                        reference = HasValueReference.Inferred(
                                                                            source = Unit,
                                                                            value = -1,
                                                                            type = BsonInt32,
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `should emit group stage with bottomN accumulator with a schema field reference`() {
        assertGeneratedQuery(
            """
          var collection = ""
          var database = ""
          
          db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}group": {"_id": null, "bottomNField": {"${'$'}bottomN": {"sortBy": {"field0": -1, }, "output": "${'$'}field0", "n": 3, }}, }}, ])
            """.trimIndent()
        ) {
            Node(
                source = Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        children = listOf(
                            Node(
                                source = Unit,
                                components = listOf(
                                    Named(Name.GROUP),
                                    HasFieldReference(
                                        reference = HasFieldReference.Inferred(
                                            source = Unit,
                                            fieldName = "_id",
                                            displayName = "_id"
                                        )
                                    ),
                                    HasValueReference(
                                        reference = HasValueReference.Constant(
                                            source = Unit,
                                            value = null,
                                            type = BsonNull,
                                        )
                                    ),
                                    HasAccumulatedFields(
                                        children = listOf(
                                            Node(
                                                source = Unit,
                                                components = listOf(
                                                    Named(Name.BOTTOM_N),
                                                    HasFieldReference(
                                                        reference = HasFieldReference.Computed(
                                                            source = Unit,
                                                            fieldName = "bottomNField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        reference = HasValueReference.Computed(
                                                            source = Unit,
                                                            type = ComputedBsonType(
                                                                baseType = BsonAny,
                                                                expression = Node(
                                                                    source = Unit,
                                                                    components = listOf(
                                                                        HasFieldReference(
                                                                            reference = HasFieldReference.FromSchema(
                                                                                source = Unit,
                                                                                fieldName = "field0",
                                                                                displayName = "field0"
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasSorts(
                                                        children = listOf(
                                                            Node(
                                                                source = Unit,
                                                                components = listOf(
                                                                    HasFieldReference(
                                                                        reference = HasFieldReference.FromSchema(
                                                                            source = Unit,
                                                                            fieldName = "field0",
                                                                            displayName = "field0"
                                                                        )
                                                                    ),
                                                                    HasValueReference(
                                                                        reference = HasValueReference.Inferred(
                                                                            source = Unit,
                                                                            value = -1,
                                                                            type = BsonInt32,
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    ),
                                                    HasLimit(3)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

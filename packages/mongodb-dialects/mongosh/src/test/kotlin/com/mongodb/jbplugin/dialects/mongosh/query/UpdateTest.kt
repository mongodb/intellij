package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class UpdateTest {
    @ParameterizedTest
    @MethodSource("allSetterLikeOperators")
    fun `generates an update one query with a simple setter-like operator`(name: Name) {
        assertGeneratedQuery(
            """
          db.getSiblingDB("myDb").getCollection("myColl").updateOne({}, {"${'$'}${name.canonical}": {"myField": "myValue", }, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myColl"))
                    ),
                    IsCommand(IsCommand.CommandType.UPDATE_ONE),
                    HasUpdates(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(name),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myValue", BsonString)
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("allSetterLikeOperators")
    fun `generates an update many query with a simple setter-like operator`(name: Name) {
        assertGeneratedQuery(
            """
          db.getSiblingDB("myDb").getCollection("myColl").updateMany({}, {"${'$'}${name.canonical}": {"myField": "myValue", }, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myColl"))
                    ),
                    IsCommand(IsCommand.CommandType.UPDATE_MANY),
                    HasUpdates(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(name),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myValue", BsonString)
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `supports pull with a filter`() {
        assertGeneratedQuery(
            """
          db.getSiblingDB("myDb").getCollection("myColl").updateMany({}, {"${'$'}pull": {"myField": {"innerField": 123}, }, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myColl"))
                    ),
                    IsCommand(IsCommand.CommandType.UPDATE_MANY),
                    HasUpdates(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.PULL),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasFilter(
                                        listOf(
                                            Node(
                                                Unit,
                                                listOf(
                                                    HasFieldReference(
                                                        HasFieldReference.FromSchema(
                                                            Unit,
                                                            "innerField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        HasValueReference.Constant(
                                                            Unit,
                                                            123,
                                                            BsonInt32
                                                        )
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
        }
    }

    @Test
    fun `supports pullAll with a list of elements`() {
        assertGeneratedQuery(
            """
          db.getSiblingDB("myDb").getCollection("myColl").updateMany({}, {"${'$'}pullAll": {"myField": [1, 2, 3], }, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myColl"))
                    ),
                    IsCommand(IsCommand.CommandType.UPDATE_MANY),
                    HasUpdates(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.PULL_ALL),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(
                                            Unit,
                                            listOf(1, 2, 3),
                                            BsonArray(BsonInt32)
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

    companion object {
        @JvmStatic
        fun allSetterLikeOperators(): Array<Name> = arrayOf(
            Name.INC,
            Name.MIN,
            Name.MAX,
            Name.SET,
            Name.SET_ON_INSERT,
            Name.UNSET,
            Name.ADD_FIELDS,
            Name.POP,
            Name.PUSH,
            Name.PULL
        )
    }
}

package com.mongodb.jbplugin.mql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionSchemaTest {
    @Test
    fun `should return the type of a field in the root object`() {
        val schema =
            CollectionSchema(
                Namespace("a", "b"),
                BsonObject(
                    mapOf(
                        "myField" to BsonInt32,
                    ),
                ),
            )

        assertEquals(BsonInt32, schema.typeOf("myField"))
    }

    @Test
    fun `should be able to merge when multiple options inside an object`() {
        val schema =
            CollectionSchema(
                Namespace("a", "b"),
                BsonObject(
                    mapOf(
                        "myField" to
                            BsonAnyOf(
                                BsonString,
                                BsonInt32,
                            ),
                    ),
                ),
            )

        assertEquals(
            BsonAnyOf(
                BsonString,
                BsonInt32,
            ),
            schema.typeOf("myField"),
        )
    }

    @Test
    fun `should be able to iterate over an object`() {
        val schema =
            CollectionSchema(
                Namespace("a", "b"),
                BsonObject(
                    mapOf(
                        "myField" to
                            BsonObject(
                                mapOf(
                                    "inner" to
                                        BsonAnyOf(
                                            BsonString,
                                            BsonObject(
                                                mapOf("otherField" to BsonDouble),
                                            )
                                        ),
                                ),
                            ),
                    ),
                ),
            )

        assertEquals(
            BsonAnyOf(
                BsonNull,
                BsonDouble,
            ),
            schema.typeOf("myField.inner.otherField"),
        )
    }

    @Test
    fun `should be able to iterate over an array with objects`() {
        val schema =
            CollectionSchema(
                Namespace("a", "b"),
                BsonObject(
                    mapOf(
                        "myField" to
                            BsonArray(
                                BsonAnyOf(
                                    BsonString,
                                    BsonObject(
                                        mapOf("otherField" to BsonDouble),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        assertEquals(
            BsonAnyOf(
                BsonNull,
                BsonDouble,
            ),
            schema.typeOf("myField.0.otherField"),
        )
    }

    @Test
    fun `returns all elements for autocompletion`() {
        val schema =
            CollectionSchema(
                Namespace("a", "b"),
                BsonObject(
                    mapOf(
                        "myField" to
                            BsonArray(
                                BsonAnyOf(
                                    BsonString,
                                    BsonDouble,
                                ),
                            ),
                    ),
                ),
            )

        val acElements = schema.allFieldNamesQualified()
        assertEquals(1, acElements.size)
        assertEquals(
            "myField" to BsonArray(BsonAnyOf(BsonString, BsonDouble)),
            acElements.first()
        )
    }
}

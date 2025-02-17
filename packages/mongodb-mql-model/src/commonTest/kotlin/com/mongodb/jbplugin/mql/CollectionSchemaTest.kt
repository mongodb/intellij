package com.mongodb.jbplugin.mql

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionSchemaTest {
    @Test
    fun should_return_the_type_of_a_field_in_the_root_object() {
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
    fun should_be_able_to_merge_when_multiple_options_inside_an_object() {
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
    fun should_be_able_to_iterate_over_an_array_with_objects() {
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
}

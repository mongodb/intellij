package com.mongodb.jbplugin.mql

import kotlin.test.Test
import kotlin.test.assertEquals

class DataDistributionTest {
    @Test
    fun returns_a_distribution_for_simple_key_value_pairs() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to "MongoDB"
                ),
                mapOf(
                    "name" to "MongoDB",
                ),
                mapOf(
                    "name" to "BongoDB",
                ),
            )
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "MongoDB" to 66.66666666666667,
                "BongoDB" to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name")!!
        )
    }

    @Test
    fun returns_a_distribution_for_simple_key_value_pairs_with_mixed_primitive_value_types() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to "MongoDB"
                ),
                mapOf(
                    "name" to "MongoDB",
                ),
                mapOf(
                    "name" to 10
                ),
                mapOf(
                    "name" to 10
                ),
                mapOf(
                    "name" to 0
                ),
                mapOf(
                    "name" to true
                ),
                mapOf(
                    "name" to false
                ),
                mapOf(
                    "name" to false
                ),
                mapOf(
                    "name" to null
                ),
                mapOf()
            )
        )

        assertEquals(
            mapOf(
                "MongoDB" to 20.0,
                10 to 20.0,
                0 to 10.0,
                true to 10.0,
                false to 20.0,
                null to 10.0,
                JsonUndefined to 10.0,
            ),
            distribution.getDistributionForPath("name")
        )
    }

    @Test
    fun returns_a_distribution_for_key_value_pairs_where_value_is_a_List_of_maps() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to listOf(
                        mapOf(
                            "firstName" to "Mongo"
                        ),
                        mapOf(
                            "lastName" to "DB"
                        ),
                    ),
                ),
                mapOf(
                    "name" to listOf(
                        mapOf(
                            "firstName" to "Bongo"
                        ),
                        mapOf(
                            "lastName" to "DB"
                        ),
                    ),
                ),
                mapOf(
                    "name" to emptyList<String>()
                ),
            )
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                JsonArray to 100.0
            ),
            distribution.getDistributionForPath("name")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "Mongo" to 33.333333333333336,
                "Bongo" to 33.333333333333336,
                JsonUndefined to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name.firstName")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "DB" to 66.66666666666667,
                JsonUndefined to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name.lastName")!!
        )
    }

    @Test
    fun returns_a_distribution_for_key_value_pairs_where_value_is_a_Map() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to mapOf(
                        "firstName" to "Mongo",
                        "lastName" to "DB",
                    )
                ),
                mapOf(
                    "name" to mapOf(
                        "firstName" to "Bongo",
                        "lastName" to "DB",
                    )
                ),
                mapOf()
            )
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                JsonObject to 66.66666666666667,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "Mongo" to 33.333333333333336,
                "Bongo" to 33.333333333333336,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name.firstName")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "DB" to 66.66666666666667,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name.lastName")!!
        )
    }

    @Test
    fun returns_a_distribution_for_key_value_pairs_where_value_is_mix_of_primitive_and_map_values() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to "MongoDB"
                ),
                mapOf(
                    "name" to "MongoDB",
                ),
                mapOf(
                    "name" to mapOf(
                        "firstName" to "BongoDB",
                        "lastName" to "MongoDB",
                    ),
                ),
                mapOf(
                    "name" to mapOf(
                        "firstName" to "Bingo",
                        "lastName" to "Normo",
                    ),
                )
            )
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "MongoDB" to 50.0,
                JsonObject to 50.0,
            ),
            distribution.getDistributionForPath("name")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "BongoDB" to 25.0,
                "Bingo" to 25.0,
                JsonUndefined to 50.0
            ),
            distribution.getDistributionForPath("name.firstName")!!
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "MongoDB" to 25.0,
                "Normo" to 25.0,
                JsonUndefined to 50.0
            ),
            distribution.getDistributionForPath("name.lastName")!!
        )
    }

    @Test
    fun returns_a_distribution_for_key_value_pairs_where_value_is_mix_of_primitive_and_list_values() {
        val distribution = DataDistribution.generate(
            listOf(
                mapOf(
                    "name" to "MongoDB"
                ),
                mapOf(
                    "name" to "MongoDB",
                ),
                mapOf(
                    "name" to listOf(
                        "MongoDB"
                    ),
                ),
                mapOf(
                    "name" to arrayOf(
                        "MongoDB"
                    ),
                )
            )
        )

        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "MongoDB" to 50.0,
                JsonArray to 50.0,
            ),
            distribution.getDistributionForPath("name")!!
        )
    }
}

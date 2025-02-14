package com.mongodb.jbplugin.mql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataDistributionTest {
    @Test
    fun `returns a distribution for simple key value pairs`() {
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

        assertEquals(
            mapOf(
                "MongoDB" to 66.66666666666667,
                "BongoDB" to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name")
        )
    }

    @Test
    fun `returns a distribution for simple key value pairs with mixed primitive value types`() {
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
    fun `returns a distribution for key value pairs where value is a List of maps`() {
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

        assertEquals(
            mapOf(
                JsonArray to 100.0
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "Mongo" to 33.333333333333336,
                "Bongo" to 33.333333333333336,
                JsonUndefined to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "DB" to 66.66666666666667,
                JsonUndefined to 33.333333333333336,
            ),
            distribution.getDistributionForPath("name.lastName")
        )
    }

    @Test
    fun `returns a distribution for key value pairs where value is a Map`() {
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

        assertEquals(
            mapOf(
                JsonObject to 66.66666666666667,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "Mongo" to 33.333333333333336,
                "Bongo" to 33.333333333333336,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "DB" to 66.66666666666667,
                JsonUndefined to 33.333333333333336
            ),
            distribution.getDistributionForPath("name.lastName")
        )
    }

    @Test
    fun `returns a distribution for key value pairs where value is mix of primitive and map values`() {
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

        assertEquals(
            mapOf(
                "MongoDB" to 50.0,
                JsonObject to 50.0,
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "BongoDB" to 25.0,
                "Bingo" to 25.0,
                JsonUndefined to 50.0
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "MongoDB" to 25.0,
                "Normo" to 25.0,
                JsonUndefined to 50.0
            ),
            distribution.getDistributionForPath("name.lastName")
        )
    }

    @Test
    fun `returns a distribution for key value pairs where value is mix of primitive and list values`() {
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

        assertEquals(
            mapOf(
                "MongoDB" to 50.0,
                JsonArray to 50.0,
            ),
            distribution.getDistributionForPath("name")
        )
    }
}

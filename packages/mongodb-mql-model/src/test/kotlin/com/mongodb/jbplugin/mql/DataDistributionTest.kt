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
                "MongoDB" to 66,
                "BongoDB" to 33,
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
                "MongoDB" to 20,
                10 to 20,
                0 to 10,
                true to 10,
                false to 20,
                null to 10,
                JsonUndefined to 10,
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
                JsonArray to 100
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "Mongo" to 33,
                "Bongo" to 33,
                JsonUndefined to 33,
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "DB" to 66,
                JsonUndefined to 33,
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
                JsonObject to 66,
                JsonUndefined to 33
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "Mongo" to 33,
                "Bongo" to 33,
                JsonUndefined to 33
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "DB" to 66,
                JsonUndefined to 33
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
                "MongoDB" to 50,
                JsonObject to 50,
            ),
            distribution.getDistributionForPath("name")
        )

        assertEquals(
            mapOf(
                "BongoDB" to 25,
                "Bingo" to 25,
                JsonUndefined to 50
            ),
            distribution.getDistributionForPath("name.firstName")
        )

        assertEquals(
            mapOf(
                "MongoDB" to 25,
                "Normo" to 25,
                JsonUndefined to 50
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
                "MongoDB" to 50,
                JsonArray to 50,
            ),
            distribution.getDistributionForPath("name")
        )
    }
}

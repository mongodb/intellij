package com.mongodb.jbplugin.mql

data object JsonArray
data object JsonObject
data object JsonUndefined

typealias Field = String
typealias Value = Any?
typealias OccurrencePercentage = Int
typealias OccurrenceCount = Int

data class DataDistribution(private val distribution: Map<Field, Map<Value, OccurrencePercentage>>) {

    fun getDistributionForPath(fieldPath: Field): Map<Value, OccurrencePercentage>? {
        return distribution.getOrDefault(fieldPath, null)
    }

    companion object {
        fun generate(sampleDocs: List<Map<Field, Value>>): DataDistribution {
            val distribution: MutableMap<Field, MutableMap<Value, OccurrenceCount>> = mutableMapOf()
            populateDistributionForList(sampleDocs, distribution)
            populateUndefinedFieldPaths(sampleDocs, distribution)
            return DataDistribution(
                calculatePercentageDistribution(
                    distribution,
                    sampleDocs.size
                )
            )
        }

        private fun populateDistributionForList(
            sampleDocs: List<Map<Field, Value>>,
            distribution: MutableMap<Field, MutableMap<Value, OccurrenceCount>>,
            parentPath: Field = "",
        ) {
            for (sampleDoc in sampleDocs) {
                for ((field, value) in sampleDoc) {
                    val fieldPath = if (parentPath.isEmpty()) field else "$parentPath.$field"
                    val fieldDistribution = distribution.getOrPut(fieldPath) { mutableMapOf() }
                    when (value) {
                        is Map<*, *> -> {
                            fieldDistribution[JsonObject] =
                                fieldDistribution.getOrDefault(JsonObject, 0) + 1
                            val mappedValue = value.map { it.key.toString() to it.value }.toMap()
                            populateDistributionForList(
                                listOf(mappedValue),
                                distribution,
                                fieldPath
                            )
                        }

                        is Array<*> -> {
                            fieldDistribution[JsonArray] =
                                fieldDistribution.getOrDefault(JsonArray, 0) + 1
                            val listOfMaps = value.filterIsInstance<Map<String, Any?>>()
                            populateDistributionForList(listOfMaps, distribution, fieldPath)
                        }

                        is Collection<*> -> {
                            fieldDistribution[JsonArray] =
                                fieldDistribution.getOrDefault(JsonArray, 0) + 1
                            val listOfMaps = value.filterIsInstance<Map<String, Any?>>()
                            populateDistributionForList(listOfMaps, distribution, fieldPath)
                        }

                        else -> {
                            fieldDistribution[value] = fieldDistribution.getOrDefault(value, 0) + 1
                        }
                    }
                }
            }
        }

        private fun populateUndefinedFieldPaths(
            sampleDocuments: List<Map<Field, Value>>,
            distribution: MutableMap<Field, MutableMap<Value, OccurrenceCount>>,
        ) {
            for (sampleDocument in sampleDocuments) {
                val fieldPathsFromDocument = getAllFieldPathsFromDocument(sampleDocument)
                for ((field, fieldDistribution) in distribution) {
                    if (!fieldPathsFromDocument.contains(field)) {
                        fieldDistribution[JsonUndefined] =
                            fieldDistribution.getOrDefault(JsonUndefined, 0) + 1
                    }
                }
            }
        }

        private fun getAllFieldPathsFromDocument(
            sampleDocument: Map<Field, Value>,
            paths: MutableSet<String> = mutableSetOf(),
            currentPath: Field = "",
        ): Set<String> {
            for ((field, value) in sampleDocument) {
                val fieldPath = if (currentPath.isEmpty()) field else "$currentPath.$field"
                paths.add(fieldPath)

                when (value) {
                    is Map<*, *> -> {
                        val mappedValue = value.map { it.key.toString() to it.value }.toMap()
                        getAllFieldPathsFromDocument(mappedValue, paths, fieldPath)
                    }

                    is Array<*> -> {
                        value.filterIsInstance<Map<String, Any?>>().forEach {
                            getAllFieldPathsFromDocument(it, paths, fieldPath)
                        }
                    }

                    is Collection<*> -> {
                        value.filterIsInstance<Map<String, Any?>>().forEach {
                            getAllFieldPathsFromDocument(it, paths, fieldPath)
                        }
                    }
                }
            }
            return paths
        }

        private fun calculatePercentageDistribution(
            distribution: MutableMap<Field, MutableMap<Value, OccurrenceCount>>,
            totalDocsExamined: Int,
        ): Map<Field, Map<Value, OccurrencePercentage>> {
            val percentDistribution: MutableMap<Field, MutableMap<Value, OccurrencePercentage>> =
                mutableMapOf()
            for ((field, fieldDistribution) in distribution) {
                val percentFieldDistribution: MutableMap<Value, OccurrencePercentage> =
                    mutableMapOf()
                for ((value, occurrenceCount) in fieldDistribution) {
                    percentFieldDistribution[value] =
                        occurrenceCount.times(100).div(totalDocsExamined)
                }
                percentDistribution[field] = percentFieldDistribution
            }
            return percentDistribution
        }
    }
}

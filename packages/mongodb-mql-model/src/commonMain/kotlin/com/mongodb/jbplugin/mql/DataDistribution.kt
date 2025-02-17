package com.mongodb.jbplugin.mql

/**
 * These are just placeholder values to represent the corresponding distribution of a field and for that reason
 * not really meant to be used outside of this module, at the best in test.
 */
internal data object JsonArray
internal data object JsonObject
internal data object JsonUndefined

typealias Field = String
typealias Value = Any?
typealias OccurrencePercentage = Double
typealias OccurrenceCount = Int

data class DataDistribution(private val distribution: Map<Field, Map<Value, OccurrencePercentage>>) {

    /**
     * Returns selectivity for a particular field path.
     * Lower number for a given field and value means the field has a lower distribution for tha value and hence higher
     * selectivity when a query asks for that particular value.
     */
    fun getSelectivityForPath(fieldPath: Field, value: Value): Double? {
        val fieldDistribution = getDistributionForPath(fieldPath) ?: return null
        val valueDistribution = fieldDistribution[value] ?: return null
        return valueDistribution
    }

    fun getDistributionForPath(fieldPath: Field): Map<Value, OccurrencePercentage>? {
        return distribution.getOrElse(fieldPath) { null }
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
                                (fieldDistribution.getOrElse(JsonObject) { 0 }) + 1
                            val mappedValue = value.map { it.key.toString() to it.value }.toMap()
                            populateDistributionForList(
                                listOf(mappedValue),
                                distribution,
                                fieldPath
                            )
                        }

                        is Array<*> -> {
                            fieldDistribution[JsonArray] =
                                (fieldDistribution.getOrElse(JsonArray) { 0 }) + 1
                            val listOfMaps = value.filterIsInstance<Map<String, Any?>>()
                            populateDistributionForList(listOfMaps, distribution, fieldPath)
                        }

                        is Collection<*> -> {
                            fieldDistribution[JsonArray] =
                                (fieldDistribution.getOrElse(JsonArray) { 0 }) + 1
                            val listOfMaps = value.filterIsInstance<Map<String, Any?>>()
                            populateDistributionForList(listOfMaps, distribution, fieldPath)
                        }

                        else -> {
                            fieldDistribution[value] = fieldDistribution.getOrElse(value) { 0 } + 1
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
                            fieldDistribution.getOrElse(JsonUndefined) { 0 } + 1
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
                        occurrenceCount.toDouble().times(100).div(totalDocsExamined.toDouble())
                }
                percentDistribution[field] = percentFieldDistribution
            }
            return percentDistribution
        }
    }
}

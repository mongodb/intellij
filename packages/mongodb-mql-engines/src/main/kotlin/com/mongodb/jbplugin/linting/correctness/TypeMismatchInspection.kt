package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.components.ParsedValueReference
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.extractValueReferencesRelevantForIndexing
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.components.schemaFieldReference
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.otherwise
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.parser.zip

data class TypeMismatchInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val documentsSampleSize: Int,
    val typeFormatter: (BsonType) -> String
)

class TypeMismatchInspection<D> : QueryInspection<
    TypeMismatchInspectionSettings<D>,
    TypeMismatch,
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, TypeMismatch>,
        settings: TypeMismatchInspectionSettings<D>
    ) {
        val querySchema = knownCollection<Source>()
            .filter {
                it.namespace.isValid &&
                    it.namespace.isNamespaceAvailableInCluster(
                        dataSource = settings.dataSource,
                        readModelProvider = settings.readModelProvider
                    )
            }
            .map {
                settings.readModelProvider.slice(
                    settings.dataSource,
                    GetCollectionSchema.Slice(it.namespace, settings.documentsSampleSize)
                ).schema
            }.parse(query)

        when (querySchema) {
            is Either.Right -> {
                val collectionSchema = querySchema.value

                val allFieldsAndValuesResult = allNodesWithSchemaFieldReferences<Source>()
                    .map { node -> node.filter { it.component<Named>()?.name != Name.SORT } }
                    .mapMany(
                        schemaFieldReference<Source>()
                            .zip(
                                first(
                                    extractValueReferencesRelevantForIndexing<Source>().map { it },
                                    otherwise { null }
                                )
                            )
                    )
                    .parse(query)

                val allFieldsAndValues = allFieldsAndValuesResult.orElse { emptyList() }

                allFieldsAndValues.filter {
                    it.second != null && isCandidateForWarning(collectionSchema, it)
                }.forEach { (fieldRef, parsedValueRef) ->
                    val fieldName = fieldRef.fieldName
                    val fieldType = settings.typeFormatter(collectionSchema.typeOf(fieldName))
                    val valueType = settings.typeFormatter(parsedValueRef!!.type)

                    holder.register(
                        QueryInsight.typeMismatch(
                            query = query,
                            source = parsedValueRef.source,
                            field = fieldName,
                            fieldType = fieldType,
                            valueType = valueType
                        )
                    )
                }
            }
            else -> {}
        }
    }

    private fun <S> isCandidateForWarning(
        collectionSchema: CollectionSchema,
        pair: Pair<HasFieldReference.FromSchema<S>, ParsedValueReference<S, out Any>?>
    ): Boolean {
        val fieldType = collectionSchema.typeOf(pair.first.fieldName)
        val valueType = pair.second!!.type

        return fieldType != BsonNull && !valueType.isAssignableTo(fieldType)
    }
}

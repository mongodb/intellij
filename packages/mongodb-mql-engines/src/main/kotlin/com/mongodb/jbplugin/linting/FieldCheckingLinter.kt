/**
 * Linter that checks that fields exist in the provided namespace.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.Inspection.FieldValueTypeMismatch
import com.mongodb.jbplugin.Inspection.NonExistentField
import com.mongodb.jbplugin.QueryInsight
import com.mongodb.jbplugin.QueryInsightsHolder
import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.parser.components.NoFieldReference
import com.mongodb.jbplugin.mql.parser.components.ParsedValueReference
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.extractValueReference
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.components.schemaFieldReference
import com.mongodb.jbplugin.mql.parser.components.schemaFieldReferences
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.parser.zip

data class FieldCheckingSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val documentsSampleSize: Int
)

/**
 * Linter that verifies that all fields that are referenced in a query do exist in the target collection.
 */
class FieldCheckingLinter<DataSource> :
    QueryInspection<FieldCheckingSettings<DataSource>, Inspection.FieldCheckInspection> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, Inspection.FieldCheckInspection>,
        settings: FieldCheckingSettings<DataSource>
    ) {
        val querySchema = knownCollection<Source>()
            .filter { it.namespace.isValid }
            .map {
                settings.readModelProvider.slice(
                    settings.dataSource,
                    GetCollectionSchema.Slice(it.namespace, settings.documentsSampleSize)
                ).schema
            }.parse(query)

        when (querySchema) {
            is Either.Right -> {
                val collectionSchema = querySchema.value
                val extractFieldExistenceWarning = schemaFieldReferences<Source>()
                    .mapMany { toFieldNotExistingWarning(query, holder, collectionSchema, it) }
                    .mapError { NoFieldReference }

                val extractTypeMismatchWarning = schemaFieldReference<Source>()
                    .filter { collectionSchema.typeOf(it.fieldName) != BsonNull }
                    .zip(extractValueReference())
                    .map { listOf(toValueMismatchWarning(query, holder, collectionSchema, it)) }
                    .mapError { NoFieldReference }

                val allFieldAndValueReferencesParser = allNodesWithSchemaFieldReferences<Source>().mapMany(
                    first(
                        extractTypeMismatchWarning,
                        extractFieldExistenceWarning,
                    )
                )

                allFieldAndValueReferencesParser.parse(query)
            }
            else -> {} // do nothing
        }
    }

    private suspend fun <Source> toFieldNotExistingWarning(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, Inspection.FieldCheckInspection>,
        collectionSchema: CollectionSchema,
        known: HasFieldReference.FromSchema<Source>
    ): Either<Any, Unit> {
        val fieldType = collectionSchema.typeOf(known.fieldName)
        if (fieldType == BsonNull) {
            holder.register(
                QueryInsight(
                    query = query,
                    source = known.source,
                    description = "com.mongodb.jbplugin.inspections.correctness.field-not-existing",
                    descriptionArguments = listOf(
                        known.fieldName,
                        collectionSchema.namespace.toString()
                    ),
                    inspection = Inspection.NonExistentField,
                )
            )
        }

        return Either.right(Unit)
    }

    private suspend fun <Source> toValueMismatchWarning(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, Inspection.FieldCheckInspection>,
        collectionSchema: CollectionSchema,
        pair: Pair<HasFieldReference.FromSchema<Source>, ParsedValueReference<Source, out Any>>
    ) {
        val fieldType = collectionSchema.typeOf(pair.first.fieldName)
        val fieldName = pair.first.fieldName
        val valueSource = pair.second.source
        val valueType = pair.second.type

        if (!valueType.isAssignableTo(fieldType)) {
            holder.register(
                QueryInsight(
                    query = query,
                    source = valueSource,
                    description = "com.mongodb.jbplugin.inspections.correctness.value-mismatch",
                    descriptionArguments = listOf(fieldName),
                    inspection = Inspection.FieldValueTypeMismatch,
                )
            )
        }
    }
}

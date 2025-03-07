/**
 * Linter that checks that fields exist in the provided namespace.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
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
import com.mongodb.jbplugin.mql.parser.filterNotNullMany
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.otherwise
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.parser.zip

/**
 * Marker type for the result of the type.
 *
 * @see FieldDoesNotExist for warnings on fields do not existing.
 *
 * @param S
 */
sealed interface FieldCheckWarning<S> {
    /**
     * Warning that is emitted when the field does not exist in the provided namespace.
     *
     * @param S
     * @property field
     * @property namespace
     * @property source
     */
    data class FieldDoesNotExist<S>(
        val source: S,
        val field: String,
        val namespace: Namespace,
    ) : FieldCheckWarning<S>

    /**
     * Warning that is emitted when the BsonType of inspected
     * value does not match the BsonType of inspected field
     *
     * @param S
     * @property valueSource The source providing the field and value for inspection
     * @property field Text value of the field being inspected
     * @property fieldType BsonType of inspected field
     * @property valueType BsonType of inspected value
     */
    data class FieldValueTypeMismatch<S>(
        val field: String,
        val fieldType: BsonType,
        val valueSource: S,
        val valueType: BsonType,
    ) : FieldCheckWarning<S>
}

/**
 * ADT that contains the result of the field check linter.
 *
 * @see FieldCheckWarning to understand a bit more the format of the result.
 *
 * @param S
 * @property warnings
 */
data class FieldCheckResult<S>(
    val warnings: List<FieldCheckWarning<S>>,
)

/**
 * Linter that verifies that all fields that are referenced in a query do exist in the target collection.
 */
object FieldCheckingLinter {
    suspend fun <D, S> lintQuery(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        query: Node<S>,
        documentsSampleSize: Int,
    ): FieldCheckResult<S> {
        val querySchema = knownCollection<S>()
            .filter { it.namespace.isValid }
            .map {
                readModelProvider.slice(
                    dataSource,
                    GetCollectionSchema.Slice(it.namespace, documentsSampleSize)
                ).schema
            }.parse(query)

        return FieldCheckResult(
            when (querySchema) {
                is Either.Left -> emptyList()
                is Either.Right -> {
                    val collectionSchema = querySchema.value
                    val extractFieldExistenceWarning = schemaFieldReferences<S>()
                        .mapMany { toFieldNotExistingWarning(collectionSchema, it) }
                        .filterNotNullMany()
                        .mapError { NoFieldReference }

                    val extractTypeMismatchWarning = schemaFieldReference<S>()
                        .filter { collectionSchema.typeOf(it.fieldName) != BsonNull }
                        .zip(extractValueReference())
                        .map { listOf(toValueMismatchWarning(collectionSchema, it)) }
                        .filterNotNullMany()
                        .mapError { NoFieldReference }

                    val allFieldAndValueReferencesParser = allNodesWithSchemaFieldReferences<S>().mapMany(
                        first(
                            extractTypeMismatchWarning,
                            extractFieldExistenceWarning,
                            otherwise { emptyList() },
                        )
                    )

                    val parsingResult = allFieldAndValueReferencesParser.parse(query)
                    parsingResult.orElse { emptyList() }.flatten()
                }
            }
        )
    }

    private fun <S> toFieldNotExistingWarning(
        collectionSchema: CollectionSchema,
        known: HasFieldReference.FromSchema<S>
    ): Either<Nothing, FieldCheckWarning<S>?> {
        val fieldType = collectionSchema.typeOf(known.fieldName)
        val warning = FieldCheckWarning.FieldDoesNotExist(
            known.source,
            known.fieldName,
            collectionSchema.namespace
        ).takeIf { fieldType == BsonNull } as? FieldCheckWarning<S>
        return Either.right(warning)
    }

    private fun <S> toValueMismatchWarning(
        collectionSchema: CollectionSchema,
        pair: Pair<HasFieldReference.FromSchema<S>, ParsedValueReference<S, out Any>>
    ): FieldCheckWarning<S>? {
        val fieldType = collectionSchema.typeOf(pair.first.fieldName)
        val fieldName = pair.first.fieldName
        val valueSource = pair.second.source
        val valueType = pair.second.type

        return FieldCheckWarning.FieldValueTypeMismatch(
            fieldName,
            fieldType,
            valueSource,
            valueType
        ).takeIf { !valueType.isAssignableTo(fieldType) }
    }
}

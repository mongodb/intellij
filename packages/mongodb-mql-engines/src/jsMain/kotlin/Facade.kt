@file:OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)

import com.mongodb.jbplugin.accessadapter.slice.recursivelyBuildSchema
import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.flattenAnyOfReferences
import com.mongodb.jbplugin.mql.mergeSchemaTogether
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Json
import kotlin.js.Promise

@JsExport
interface InputNamespace {
    val database: String
    val collection: String
}

@JsExport
interface Opaque<T : Any>

@JsExport
interface Query

@JsExport
fun analyzeNamespace(ns: InputNamespace, sample: Array<Json>?): Opaque<CollectionSchema> {
    val namespace = Namespace(ns.database, ns.collection)
    if (sample != null) {
        val everySchema = sample.map(::recursivelyBuildSchema)
        val consolidatedSchema = everySchema.reduceOrNull(::mergeSchemaTogether) ?: BsonObject(
            emptyMap(),
        )
        val schema = flattenAnyOfReferences(consolidatedSchema) as BsonObject
        val distribution = calculateDataDistribution(sample)
        return CollectionSchema(
            namespace,
            schema,
            distribution
        ).asDynamic()
    } else {
        return CollectionSchema(
            namespace,
            BsonObject(emptyMap()),
        ).asDynamic()
    }
}

@JsExport
fun parseQuery(filter: Json, opaqueSchema: Opaque<CollectionSchema>): Opaque<Query> {
    val query = parseFilter(filter)
    val schema = opaqueSchema.unsafeCast<CollectionSchema>()

    return query.with(HasCollectionReference(HasCollectionReference.Known(filter, filter, schema.namespace, schema)))
        .asDynamic()
}

@JsExport
interface SuggestedIndex {
    val index: Json
    val coveredQueries: Array<Json>
}

@JsExport
fun suggestIndex(queries: Array<Opaque<Query>>): Promise<SuggestedIndex> = GlobalScope.promise {
    val mainQuery: dynamic = queries[0]
    val siblings: Array<dynamic> = queries.drop(1).toTypedArray()

    val suggestedIndex = IndexAnalyzer.analyze(
        mainQuery,
        object : SiblingQueriesFinder<Unit> {
            override fun allSiblingsOf(query: Node<Unit>): Array<Node<Unit>> {
                return siblings
            }
        },
        CollectionIndexConsolidationOptions(indexesSoftLimit = 10)
    )

    val suggestedIndexJson = js("{}")
    when (suggestedIndex) {
        is IndexAnalyzer.SuggestedIndex.MongoDbIndex -> {
            suggestedIndex.fields.forEach {
                suggestedIndexJson[it.fieldName] = 1
            }
        }
        else -> {}
    }

    fun toNode(query: Opaque<Query>): Node<Json> {
        val dyn: dynamic = query
        return dyn
    }

    val coveredQueries = queries.map(::toNode).map { it.source }
    val result = js("{}")

    result["index"] = suggestedIndexJson
    result["coveredQueries"] = coveredQueries.toTypedArray()

    result
}

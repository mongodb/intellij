@file:OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)

import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.toBsonType
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
fun parseQuery(namespace: InputNamespace, json: Json): Opaque<Query> {
    val ns = Namespace(namespace.database, namespace.collection)

    val jsonEntries: Array<dynamic> = js("Object").entries(json)
    val filter = jsonEntries.map {
        Node(
            it,
            listOf(
                Named(Name.EQ),
                HasFieldReference(HasFieldReference.FromSchema(it[0], it[0])),
                HasValueReference(HasValueReference.Constant(it[1], it[1], (it[1] as Any).toBsonType())),
            )
        )
    }

    val result: dynamic = Node(
        json,
        listOf(
            HasCollectionReference(HasCollectionReference.Known(Unit, Unit, ns, null)),
            IsCommand(IsCommand.CommandType.FIND_MANY),
            HasFilter<Unit>(filter),
            HasLimit(50)
        )
    )

    return result
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

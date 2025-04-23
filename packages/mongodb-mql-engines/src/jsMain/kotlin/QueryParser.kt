import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

fun parseFilter(query: dynamic): Node<dynamic> = when {
    jsTypeOf(query) == "object" -> run {
        val keys = js("Object.keys(query)") as Array<String>
        val isOperatorObject = keys.all { it.startsWith("$") }
        if (isOperatorObject) {
            val children = keys.map { op ->
                val value = query[op]
                when (op) {
                    "\$and", "\$or", "\$nor" -> {
                        val arr = value as Array<dynamic>
                        val parsedChildren = arr.map { parseFilter(it) }

                        val flattened = if (op == "\$and") {
                            parsedChildren.flatMap {
                                val isAndNode = it.component<Named>()?.name?.canonical == "and"
                                val filter = it.component<HasFilter<*>>()
                                if (isAndNode && filter != null) filter.children else listOf(it)
                            }
                        } else {
                            parsedChildren
                        }

                        Node(
                            value,
                            listOf(
                                Named(Name.from(op.removePrefix("$"))),
                                HasFilter(flattened as List<Node<dynamic>>)
                            )
                        )
                    }
                    "\$not" -> {
                        Node(
                            value,
                            listOf(
                                Named(Name.from("not")),
                                HasFilter(listOf(parseFilter(value)))
                            )
                        )
                    }
                    else -> Node(
                        value,
                        listOf(
                            Named(Name.from(op.removePrefix("$"))),
                            parseEjson(value)
                        )
                    )
                }
            }
            Node(query, listOf(Named(Name.AND), HasFilter(children)))
        } else {
            // Template or nested-operator fields
            val children = keys.map { k ->
                val value = query[k]
                val valueIsObject = jsTypeOf(value) == "object"
                val valueKeys = if (valueIsObject) js("Object.keys(value)") as Array<String> else emptyArray()
                val nestedOperatorKeys = valueKeys.filter { it.startsWith("$") }

                if (nestedOperatorKeys.isNotEmpty() && nestedOperatorKeys.any { !isEjsonKey(it) }) {
                    val subconditions = nestedOperatorKeys.map { op ->
                        Node(
                            value,
                            listOf(
                                Named(Name.from(op.removePrefix("$"))),
                                HasFieldReference(HasFieldReference.FromSchema(query, k)),
                                parseEjson(value[op])
                            )
                        )
                    }
                    Node(
                        query,
                        listOf(
                            Named(Name.AND),
                            HasFilter(subconditions)
                        )
                    )
                } else {
                    Node(
                        value,
                        listOf(
                            Named(Name.EQ),
                            HasFieldReference(HasFieldReference.FromSchema(query, k)),
                            parseEjson(value)
                        )
                    )
                }
            }
            Node(query, listOf(Named(Name.AND), HasFilter(children)))
        }
    }
    else ->
        Node(query, listOf(Named(Name.EQ), parseEjson(query)))
}

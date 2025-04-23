import com.mongodb.jbplugin.mql.components.*
import kotlin.test.*

class ParseQueryTest {
    @Test
    fun parseTemplateQuery_a1_b2() {
        val query: dynamic = js("{ a: 1, b: 2 }")
        val root = parseQuery(query)

        val filter = root.component<HasFilter<*>>()
        assertNotNull(filter)
        val children = filter.children
        assertEquals(2, children.size)

        val childA = children[0]
        assertEquals("eq", childA.component<Named>()?.name?.canonical)
        assertEquals("a", (childA.component<HasFieldReference<*>>()?.reference as HasFieldReference.FromSchema).fieldName)

        val childB = children[1]
        assertEquals("eq", childB.component<Named>()?.name?.canonical)
        assertEquals("b", (childB.component<HasFieldReference<*>>()?.reference as HasFieldReference.FromSchema).fieldName)
    }

    @Test
    fun parseAndQuery() {
        val query: dynamic = js("{ '${'$'}and': [{ a: 1 }, { b: 2 }] }")
        val root = parseQuery(query)

        val andNode = root.component<HasFilter<*>>()?.children?.get(0)
        assertNotNull(andNode)
        assertEquals("and", andNode.component<Named>()?.name?.canonical)
        val subFilter = andNode.component<HasFilter<*>>()
        assertNotNull(subFilter)
        assertEquals(2, subFilter.children.size)
    }

    @Test
    fun parseOrQuery() {
        val query: dynamic = js("{ '${'$'}or': [{ a: 1 }, { b: 2 }] }")
        val root = parseQuery(query)

        val orNode = root.component<HasFilter<*>>()?.children?.get(0)
        assertNotNull(orNode)
        assertEquals("or", orNode.component<Named>()?.name?.canonical)
        val subFilter = orNode.component<HasFilter<*>>()
        assertNotNull(subFilter)
        assertEquals(2, subFilter.children.size)
    }

    @Test
    fun parseNotQuery() {
        val query: dynamic = js("{ '${'$'}not': { a: 1 } }")
        val root = parseQuery(query)

        val notNode = root.component<HasFilter<*>>()?.children?.get(0)
        assertNotNull(notNode)
        assertEquals("not", notNode.component<Named>()?.name?.canonical)
        val subFilter = notNode.component<HasFilter<*>>()
        assertNotNull(subFilter)
        assertEquals(1, subFilter.children.size)
    }

    @Test
    fun parseNestedAndOrQuery() {
        val query: dynamic = js(
            """
            {
              "${'$'}and": [
                { "a": { "${'$'}gt": 5, "${'$'}lt": 10 } },
                { "${'$'}or": [
                  { "b": 2 },
                  { "c": 3 }
                ]}
              ]
            }
        """
        )
        val root = parseQuery(query)
        val andNode = root.component<HasFilter<*>>()?.children?.get(0)
        assertNotNull(andNode)
        assertEquals("and", andNode.component<Named>()?.name?.canonical)
        val subFilter = andNode.component<HasFilter<*>>()
        assertNotNull(subFilter)
        assertEquals(2, subFilter.children.size)

        val andSubConditions = subFilter.children[0]
        assertEquals("and", andSubConditions.component<Named>()?.name?.canonical)
        val gtAndLtFilter = andSubConditions.component<HasFilter<*>>()
        assertNotNull(gtAndLtFilter)
        println(gtAndLtFilter)
        assertEquals(2, gtAndLtFilter.children.size)

        val gtNode = gtAndLtFilter.children[0]
        assertEquals("gt", gtNode.component<Named>()?.name?.canonical)
        assertEquals("a", (gtNode.component<HasFieldReference<*>>()?.reference as HasFieldReference.FromSchema).fieldName)

        val ltNode = gtAndLtFilter.children[1]
        assertEquals("lt", ltNode.component<Named>()?.name?.canonical)
        assertEquals("a", (ltNode.component<HasFieldReference<*>>()?.reference as HasFieldReference.FromSchema).fieldName)

        val orNode = subFilter.children[1]
        assertEquals("or", orNode.component<Named>()?.name?.canonical)
        val orFilter = orNode.component<HasFilter<*>>()
        assertNotNull(orFilter)
        assertEquals(2, orFilter.children.size)
    }
}

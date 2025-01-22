package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryFilter
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.hasName
import com.mongodb.jbplugin.mql.parser.components.whenIsCommand
import com.mongodb.jbplugin.mql.parser.count
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.matches
import com.mongodb.jbplugin.mql.parser.nth
import com.mongodb.jbplugin.mql.parser.parse

fun <S> Node<S>.isAggregate(): Boolean {
    return whenIsCommand<S>(IsCommand.CommandType.AGGREGATE)
        .map { true }
        .parse(this).orElse { false }
}

fun <S> Node<S>.canEmitAggregate(): Boolean {
    return aggregationStages<S>()
        .matches(count<Node<S>>().filter { it >= 1 }.matches().anyError())
        .nth(0)
        .matches(hasName(Name.MATCH))
        .map { true }
        .parse(this).orElse { false }
}

fun <S> MongoshBackend.emitAggregateBody(node: Node<S>): MongoshBackend {
    // here we can assume that we only have 1 single stage that is a match
    val matchStage = node.component<HasAggregation<S>>()!!.children[0]
    val filter = matchStage.component<HasFilter<S>>()?.children?.getOrNull(0)
    val longFilter = filter?.component<HasFilter<S>>()?.children?.size?.let { it > 3 } == true

    emitArrayStart(long = true)
    emitObjectStart()
    emitObjectKey(registerConstant('$' + "match"))
    if (filter != null) {
        emitObjectStart(long = longFilter)
        emitQueryFilter(filter)
        emitObjectEnd(long = longFilter)
    } else {
        emitComment("No filter provided.")
    }
    emitObjectEnd()
    emitArrayEnd(long = true)

    return this
}

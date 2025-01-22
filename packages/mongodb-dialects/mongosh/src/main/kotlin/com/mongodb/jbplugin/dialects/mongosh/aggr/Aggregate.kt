package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.resolveFieldReference
import com.mongodb.jbplugin.dialects.mongosh.query.resolveValueReference
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.components.whenIsCommand
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.parse

fun <S> Node<S>.isAggregate(): Boolean {
    return whenIsCommand<S>(IsCommand.CommandType.AGGREGATE)
        .map { true }
        .parse(this).orElse { false }
}

fun <S> MongoshBackend.emitAggregateBody(node: Node<S>, queryContext: QueryContext): MongoshBackend {
    val allStages = node.component<HasAggregation<S>>()?.children ?: emptyList()
    val stagesToEmit = when (queryContext.explainPlan) {
        QueryContext.ExplainPlanType.NONE -> allStages
        else -> allStages.takeWhile { it.isNotDestructive() }
    }

    emitArrayStart(long = true)
    for (stage in stagesToEmit) {
        when (stage.component<Named>()?.name) {
            Name.MATCH -> emitMatchStage(stage)
            Name.PROJECT -> emitProjectStage(stage)
            else -> {}
        }
    }
    emitArrayEnd(long = true)
    return this
}

internal fun <S> MongoshBackend.emitAsFieldValueDocument(nodes: List<Node<S>>): MongoshBackend {
    for (node in nodes) {
        val field = node.component<HasFieldReference<S>>() ?: continue
        val value = node.component<HasValueReference<S>>() ?: continue

        emitObjectKey(resolveFieldReference(field))
        emitContextValue(resolveValueReference(value, field))
        emitObjectValueEnd()
    }

    return this
}

private val NON_DESTRUCTIVE_STAGES = setOf(
    Name.MATCH
)

private fun <S> Node<S>.isNotDestructive(): Boolean {
    return component<Named>()?.name?.let { NON_DESTRUCTIVE_STAGES.contains(it) } == true
}

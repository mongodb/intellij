package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.resolveFieldReference
import com.mongodb.jbplugin.dialects.mongosh.query.resolveValueReference
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

suspend fun <S> MongoshBackend.emitAggregateBody(node: Node<S>, explainPlan: ExplainPlanType): MongoshBackend {
    val allStages = node.component<HasAggregation<S>>()?.children ?: emptyList()
    val stagesToEmit = when (explainPlan) {
        ExplainPlanType.NONE -> allStages
        else -> allStages.takeWhile { it.isNotDestructive() }
    }

    emitArrayStart(long = true)
    for (stage in stagesToEmit) {
        when (stage.component<Named>()?.name) {
            Name.MATCH -> emitMatchStage(stage)
            Name.PROJECT -> emitProjectStage(stage)
            Name.ADD_FIELDS -> emitAddFieldsStage(stage)
            Name.UNWIND -> emitUnwindStage(stage)
            Name.SORT -> emitSortStage(stage)
            Name.GROUP -> emitGroupStage(stage)
            Name.LIMIT -> emitLimitStage(stage)
            else -> {}
        }
        emitObjectValueEnd(long = true)
    }
    emitArrayEnd(long = true)
    return this
}

internal fun <S> MongoshBackend.emitAsFieldValueDocument(nodes: List<Node<S>>, isLong: Boolean = false): MongoshBackend {
    for (node in nodes) {
        val field = node.component<HasFieldReference<S>>() ?: continue
        val value = node.component<HasValueReference<S>>() ?: continue

        emitObjectKey(
            resolveFieldReference(
                fieldRef = field,
                fieldUsedAsValue = false,
            )
        )
        emitContextValue(resolveValueReference(value, field))
        emitObjectValueEnd(long = isLong)
    }

    return this
}

private val NON_DESTRUCTIVE_STAGES = setOf(
    Name.MATCH,
    Name.PROJECT,
    Name.ADD_FIELDS,
    Name.UNWIND,
    Name.SORT,
    Name.GROUP,
)

private fun <S> Node<S>.isNotDestructive(): Boolean {
    return component<Named>()?.name?.let { NON_DESTRUCTIVE_STAGES.contains(it) } == true
}

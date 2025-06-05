package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.linting.Inspection.InvalidProjection
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Name.PROJECT
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.map

class InvalidProjectionInspectionSettings

class InvalidProjectionInspection : QueryInspection<
    InvalidProjectionInspectionSettings,
    InvalidProjection,
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, InvalidProjection>,
        settings: InvalidProjectionInspectionSettings
    ) {
        val projectAggregationStages = aggregationStages<Source>()
            .map { stages -> stages.filter { it.component<Named>()?.name == PROJECT } }
            .invoke(query)
            .orElse { emptyList() }
            .mapNotNull { it.component<HasProjections<Source>>() }

        for (eachProject in projectAggregationStages) {
            val groups = eachProject.children
                .map { it.component<HasFieldReference<Source>>() to it.component<Named>() }
                .filter { it.second != null }
                .groupBy { it.second?.name!! }
                .mapValues { entry ->
                    entry.value.mapNotNull {
                        it.first?.reference as? HasFieldReference.FromSchema
                    }.map {
                        it.fieldName to it.source
                    }
                }

            val included = groups[Name.INCLUDE] ?: emptyList()
            val excluded = groups[Name.EXCLUDE] ?: emptyList()

            if (excluded.isNotEmpty() && included.isNotEmpty() && included.any { it.first != "_id" }) {
                for (inclusion in included) {
                    if (inclusion.first == "_id") {
                        continue
                    }

                    holder.register(
                        QueryInsight.invalidInclusionInExclusionProjection(
                            query,
                            inclusion.second,
                            inclusion.first,
                        )
                    )
                }
            }
        }
    }
}

package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.MatchStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.ProjectStageParser
import com.mongodb.jbplugin.mql.Node

/**
 * Parser for parsing supported patterns of writing an aggregation pipeline.
 * Supported patterns for writing aggregation calls are the following:
 * 1. MongoTemplate.aggregate()
 * 2. MongoTemplate.aggregateStream()
 *
 * The AggregationParser concerns itself only with parsing the aggregation related semantics and
 * leave the rest as a responsibility for the composing unit.
 */
class AggregationStagesParser(private val matchStageParser: MatchStageParser) {
    private val projectStageParser = ProjectStageParser()

    private fun isStageCall(stageCallMethod: PsiMethod): Boolean {
        return matchStageParser.canParse(stageCallMethod) ||
            projectStageParser.canParse(stageCallMethod)
    }

    private fun parseAggregationStages(
        newAggregationCall: PsiMethodCallExpression
    ): List<Node<PsiElement>> {
        val newAggregationCallArguments = newAggregationCall.argumentList.expressions
        val resolvedStageCalls = newAggregationCallArguments.mapNotNull { stageCallExpression ->
            stageCallExpression.resolveToMethodCallExpression { _, stageCallMethod ->
                isStageCall(stageCallMethod)
            }
        }
        return resolvedStageCalls.map { stageCall ->
            val stageCallMethod = stageCall.fuzzyResolveMethod() ?: return@map Node(
                source = stageCall,
                components = emptyList()
            )

            if (matchStageParser.canParse(stageCallMethod)) {
                matchStageParser.parse(stageCall)
            } else if (projectStageParser.canParse(stageCallMethod)) {
                projectStageParser.parse(stageCall)
            } else {
                Node(
                    source = stageCall,
                    components = emptyList()
                )
            }
        }
    }

    fun parse(aggregateRootCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        val aggregateRootCallArguments = aggregateRootCall.argumentList.expressions

        val newAggregationCallExpression = aggregateRootCallArguments.getOrNull(0)
            ?: return emptyList()

        // This is the call to Aggregation.newAggregation method which is generally the first
        // argument to the root aggregate call. All the aggregation stages are to be found as
        // the argument to this method call.
        val newAggregationCall = newAggregationCallExpression.resolveToMethodCallExpression {
                _,
                method
            ->
            method.name == "newAggregation"
        } ?: return emptyList()

        return parseAggregationStages(newAggregationCall)
    }
}

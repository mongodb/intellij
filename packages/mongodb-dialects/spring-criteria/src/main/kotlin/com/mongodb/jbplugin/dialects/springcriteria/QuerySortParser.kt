package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.SortStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.gatherChainedCalls
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.isSortObject
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.resolveToSortCreationCall
import com.mongodb.jbplugin.mql.Node

private const val QUERY_FQN = "org.springframework.data.mongodb.core.query.Query"

/**
 * Responsible for parsing `Sort` objects chained on `Query` objects.
 */
class QuerySortParser {
    fun parse(queryObjectExpression: PsiExpression?): List<Node<PsiElement>> {
        val queryMethodCallExpression =
            queryObjectExpression?.resolveToMethodCallExpression { _, method ->
                // We will resolve any method that is from Query class and then later filter
                // only for the sort method in the chain of calls
                method.containingClass?.qualifiedName == QUERY_FQN
            } ?: return emptyList()

        return queryMethodCallExpression.gatherChainedCalls().flatMap { chainedCallExpression ->
            val chainedCall = chainedCallExpression.fuzzyResolveMethod()
            when (chainedCall?.name) {
                // Query.of(Query.from()) - need to grab the argument of method and resolve sort
                // from there
                "of" -> {
                    val childQueryObjectExpression = chainedCallExpression.argumentList.expressions.getOrNull(
                        0
                    )
                    if (childQueryObjectExpression != null) {
                        parse(childQueryObjectExpression)
                    } else {
                        emptyList()
                    }
                }
                // Query.query(...).with(sortObject) - This is our Sort attaching call
                "with" -> {
                    val maybeSortObjectExpression = chainedCallExpression.argumentList.expressions.getOrNull(
                        0
                    )
                    if (maybeSortObjectExpression?.isSortObject() == true) {
                        val sortCreationMethodCall =
                            maybeSortObjectExpression.resolveToSortCreationCall()
                                ?: return@flatMap emptyList()

                        SortStageParser.parseSortObjectArgument(
                            sortCreationMethodCall = sortCreationMethodCall,
                            // There is no parent chain just yet
                            directionForcedFromParentChain = null,
                            reverseCountForcedFromParentChain = 0
                        )
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }
    }
}

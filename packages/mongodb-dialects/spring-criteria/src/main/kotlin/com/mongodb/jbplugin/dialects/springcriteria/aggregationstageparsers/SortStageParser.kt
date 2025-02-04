package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.getVarArgsOrIterableArgs
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveElementUntil
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveFieldNameFromExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToIterableCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

internal const val SORT_OPERATION_FQN = "org.springframework.data.mongodb.core.aggregation.SortOperation"
internal const val SORT_FQN = "org.springframework.data.domain.Sort"
internal const val DIRECTION_FQN = "org.springframework.data.domain.Sort.Direction"
internal const val ORDER_FQN = "org.springframework.data.domain.Sort.Order"

class SortStageParser : StageParser {
    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ): Boolean {
        val isInStageCreationCall = canParse(method)
        return isInStageCreationCall ||
            method.isSortCreationMethod() ||
            method.isOrderCreationMethod()
    }

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val methodFqn = stageCallMethod.containingClass?.qualifiedName ?: return false
        return listOf(AGGREGATE_FQN, SORT_OPERATION_FQN).contains(methodFqn) &&
            // we consider the root call sort and any chained calls which we currently
            // support
            listOf("sort", "and").contains(stageCallMethod.name)
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        return createSortNode(
            source = stageCall,
            criteria = stageCall.gatherChainedCalls().flatMap { methodCall ->
                val method = methodCall.fuzzyResolveMethod() ?: return@flatMap emptyList()
                when (method.name) {
                    "sort", "and" -> parseSortOrAndCall(methodCall)
                    else -> emptyList()
                }
            }
        )
    }

    /**
     * Parses a `Aggregation.sort` or `SortOperation.and` call and returns a list of MQL Node
     * representing sort criteria. Both methods has two overloads both which we support:
     * 1. `Aggregation.sort(Sort sort)`
     * 2. `Aggregation.sort(Direction direction, String... fields)`
     * 3. `SortOperation.and(Sort sort)`
     * 4. `SortOperation.sort(Direction direction, String... fields)`
     */
    private fun parseSortOrAndCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        val firstArgumentExpression = methodCall.argumentList.expressions.getOrNull(0)
            ?: return emptyList()

        return if (firstArgumentExpression.isDirectionEnum()) {
            parseDirectionArgumentAndFieldsToSortCriteria(
                directionArgumentExpression = firstArgumentExpression,
                fieldArgumentExpressions = methodCall.argumentList.expressions.drop(1),
                // There is no parent chain just yet
                directionForcedFromParentChain = null,
                reverseCountForcedFromParentChain = 0
            )
        } else if (firstArgumentExpression.isSortObject()) {
            parseSortObjectArgument(
                sortCreationMethodCall = firstArgumentExpression.resolveToSortCreationCall()!!,
                // There is no parent chain just yet
                directionForcedFromParentChain = null,
                reverseCountForcedFromParentChain = 0
            )
        } else {
            emptyList()
        }
    }

    companion object {
        private fun parseDirectionArgumentAndFieldsToSortCriteria(
            directionArgumentExpression: PsiExpression,
            fieldArgumentExpressions: List<PsiExpression>,
            directionForcedFromParentChain: Name?,
            reverseCountForcedFromParentChain: Int
        ): List<Node<PsiElement>> {
            val directionName = directionArgumentExpression.resolveToDirectionName().derivedDirection(
                directionForcedFromParentChain,
                reverseCountForcedFromParentChain
            )
            return fieldArgumentExpressions.map { fieldExpression ->
                createSortCriteria(
                    directionName = directionName,
                    fieldExpression = fieldExpression,
                    directionExpression = directionArgumentExpression
                )
            }
        }

        internal fun parseSortObjectArgument(
            sortCreationMethodCall: PsiMethodCallExpression,
            directionForcedFromParentChain: Name?,
            reverseCountForcedFromParentChain: Int,
        ): List<Node<PsiElement>> {
            // This method can be called recursively in which case the caller will be considered parsing
            // a parent chain and the current call will become the child chain. Each child chain inherit
            // the forcedDirection and reverseCount from the parent chain so that they can derive correct
            // values for themselves
            var forcedDirection = directionForcedFromParentChain
            var reverseCount = reverseCountForcedFromParentChain

            return sortCreationMethodCall.gatherChainedCalls().flatMapIndexed { index, methodCall ->
                val method = methodCall.fuzzyResolveMethod()

                if (method?.containingClass?.qualifiedName != SORT_FQN) {
                    return@flatMapIndexed emptyList<Node<PsiElement>>()
                }

                if (method.name == "ascending" || method.name == "descending") {
                    // If we have ascending or descending as the last call in the chain and there was no
                    // other forced direction from parent chain then the entire chain is forced to have
                    // the current order from this call.
                    if (index == 0 && forcedDirection == null) {
                        forcedDirection = if (method.name == "ascending") {
                            Name.ASCENDING
                        } else {
                            Name.DESCENDING
                        }
                    }
                    // The current order could also come before a reverse, in this chain or from a parent
                    // chain. In that case, we revert the forced direction as many times as we encountered
                    // reverse and that becomes the new forced direction for this chain
                    else if (index != 0 && forcedDirection == null) {
                        val supposedNewDirection = if (method.name == "ascending") {
                            Name.ASCENDING
                        } else {
                            Name.DESCENDING
                        }
                        forcedDirection = supposedNewDirection.derivedDirection(
                            directionForcedFromParentChain = null,
                            reverseCountForcedFromParentChain = reverseCount
                        )
                        reverseCount = 0
                    }
                    return@flatMapIndexed emptyList<Node<PsiElement>>()
                }
                // Accounting for reverse only makes sense when there is no direction forced already
                // either from the current chain or from parent chain
                else if (method.name == "reverse" && forcedDirection == null) {
                    reverseCount += 1
                    return@flatMapIndexed emptyList<Node<PsiElement>>()
                }
                // This is how we chain additional Sort criteria chains onto the current chain.
                // This chained Sort criteria will inherit the forcedDirection and reverseCount from
                // the current chain
                else if (method.name == "and") {
                    val firstArgumentExpression = methodCall.argumentList.expressions.getOrNull(0)
                    val sortCreationMethodCallPassedToAnd =
                        firstArgumentExpression?.resolveToSortCreationCall()
                            ?: return@flatMapIndexed emptyList<Node<PsiElement>>()

                    return@flatMapIndexed parseSortObjectArgument(
                        sortCreationMethodCallPassedToAnd,
                        forcedDirection,
                        reverseCount
                    )
                } else if (method.name == "by") {
                    return@flatMapIndexed parseSortByMethodCall(
                        methodCall = methodCall,
                        directionForcedFromParentChain = forcedDirection,
                        reverseCountForcedFromParentChain = reverseCount
                    )
                } else {
                    return@flatMapIndexed emptyList<Node<PsiElement>>()
                }
            }
        }

        private fun parseSortByMethodCall(
            methodCall: PsiMethodCallExpression,
            directionForcedFromParentChain: Name?,
            reverseCountForcedFromParentChain: Int
        ): List<Node<PsiElement>> {
            val firstArgumentExpression = methodCall.argumentList.expressions.getOrNull(0)
                ?: return emptyList()

            return if (firstArgumentExpression.isDirectionEnum()) {
                parseDirectionArgumentAndFieldsToSortCriteria(
                    // Kinda ambiguous if the direction was forced from parent chain
                    directionArgumentExpression = firstArgumentExpression,
                    fieldArgumentExpressions = methodCall.argumentList.expressions.drop(1),
                    directionForcedFromParentChain = directionForcedFromParentChain,
                    reverseCountForcedFromParentChain = reverseCountForcedFromParentChain
                )
            } else if (firstArgumentExpression.isOrderObject() ||
                firstArgumentExpression.isParseableJavaIterable()
            ) {
                parseOrderObjects(
                    methodCall.getVarArgsOrIterableArgs(),
                    directionForcedFromParentChain,
                    reverseCountForcedFromParentChain
                )
            } else {
                methodCall.argumentList.expressions.map { fieldExpression ->
                    createSortCriteria(
                        directionName = Name.ASCENDING.derivedDirection(
                            directionForcedFromParentChain,
                            reverseCountForcedFromParentChain
                        ),
                        fieldExpression = fieldExpression,
                        directionExpression = methodCall
                    )
                }
            }
        }

        private fun parseOrderObjects(
            orderObjectsExpression: List<PsiExpression>,
            directionForcedFromParentChain: Name?,
            reverseCountForcedFromParentChain: Int
        ): List<Node<PsiElement>> {
            return orderObjectsExpression.mapNotNull { orderExpression ->
                val orderCreationCall = orderExpression.resolveToOrderCreationCall()
                val orderCreationMethod =
                    orderCreationCall?.fuzzyResolveMethod() ?: return@mapNotNull null
                val fieldArgumentExpression =
                    orderCreationCall.argumentList.expressions.getOrNull(0)
                        ?: return@mapNotNull null
                val supposedDirectionForThisOrder = when (orderCreationMethod.name) {
                    "asc", "by" -> Name.ASCENDING
                    "desc" -> Name.DESCENDING
                    else -> Name.UNKNOWN
                }
                val finalDirectionForThisOrder = supposedDirectionForThisOrder.derivedDirection(
                    directionForcedFromParentChain,
                    reverseCountForcedFromParentChain
                )

                createSortCriteria(
                    directionName = finalDirectionForThisOrder,
                    fieldExpression = fieldArgumentExpression,
                    directionExpression = orderExpression
                )
            }
        }

        private fun createSortCriteria(
            directionName: Name,
            fieldExpression: PsiExpression,
            directionExpression: PsiElement,
        ): Node<PsiElement> {
            val fieldReference = fieldExpression.resolveFieldNameFromExpression()
            return Node(
                source = fieldExpression,
                components = listOf(
                    Named(directionName),
                    HasFieldReference(fieldReference),
                    HasValueReference(
                        reference = when (directionName) {
                            Name.ASCENDING,
                            Name.DESCENDING -> HasValueReference.Inferred(
                                source = directionExpression,
                                type = BsonInt32,
                                value = if (directionName == Name.ASCENDING) 1 else -1
                            )
                            else -> HasValueReference.Unknown
                        }
                    )
                )
            )
        }

        private fun createSortNode(
            source: PsiMethodCallExpression,
            criteria: List<Node<PsiElement>>
        ): Node<PsiElement> {
            return Node(
                source = source,
                components = listOf(
                    Named(Name.SORT),
                    HasSorts(criteria)
                )
            )
        }
    }
}

/**
 * Checks if the PsiExpression represents a Sort object
 */
fun PsiExpression.isSortObject(): Boolean {
    return resolveToSortCreationCall() != null
}

/**
 * Checks if the PsiExpression represents a Direction enum
 */
fun PsiExpression.isDirectionEnum(): Boolean {
    return resolveToDirectionEnum() != null
}

/**
 * Checks if the PsiExpression represents an Order object
 */
fun PsiExpression.isOrderObject(): Boolean {
    return resolveToOrderCreationCall() != null
}

/**
 * Checks if the PsiExpression is a parseable Java iterable
 */
fun PsiExpression.isParseableJavaIterable(): Boolean {
    return resolveToIterableCallExpression() != null
}

/**
 * Resolves a PsiExpression to a PsiMethodCallExpression that creates a Sort object.
 * A Sort object is generally created using static methods available on the Sort class. The most
 * common of those being `Sort.by` and different overloads of it.
 */
fun PsiExpression.resolveToSortCreationCall(): PsiMethodCallExpression? {
    return resolveToMethodCallExpression { _, method ->
        method.isSortCreationMethod()
    }
}

fun PsiMethod.isSortCreationMethod(): Boolean {
    return containingClass?.qualifiedName == SORT_FQN &&
        listOf(
            "ascending",
            "descending",
            "reverse",
            "and",
            "by"
        ).contains(name)
}

/**
 * Resolves a PsiExpression to a PsiEnumConstant that represents a Direction enum
 */
fun PsiExpression.resolveToDirectionEnum(): PsiEnumConstant? {
    return resolveElementUntil { element ->
        when (element) {
            is PsiEnumConstant -> element.containingClass?.qualifiedName == DIRECTION_FQN
            is PsiField -> element.type.equalsToText(DIRECTION_FQN)
            else -> false
        }
    }
}

/**
 * Helper to resolve a Name from PsiExpression that is points to a Direction enum
 */
fun PsiExpression.resolveToDirectionName(): Name {
    val resolvedDirectionEnum = resolveToDirectionEnum()
    return when (resolvedDirectionEnum?.name) {
        "ASC" -> Name.ASCENDING
        "DESC" -> Name.DESCENDING
        else -> Name.UNKNOWN
    }
}

/**
 * Resolves a PsiMethodCallExpression that is responsible for creating the Order object represented
 * by the referenced PsiExpression
 */
fun PsiExpression.resolveToOrderCreationCall(): PsiMethodCallExpression? {
    return resolveToMethodCallExpression { _, method ->
        method.isOrderCreationMethod()
    }
}

fun PsiMethod.isOrderCreationMethod(): Boolean {
    return containingClass?.qualifiedName == ORDER_FQN &&
        listOf(
            "asc",
            "desc",
            "by"
        ).contains(name)
}

/**
 * Helper to derive a Direction when there is a forced direction or reversal applied from the
 * earlier / parent chains
 */
fun Name.derivedDirection(
    directionForcedFromParentChain: Name?,
    reverseCountForcedFromParentChain: Int
): Name {
    if (directionForcedFromParentChain != null) {
        return directionForcedFromParentChain
    }

    var derivedDirection = this
    for (count in 0..<reverseCountForcedFromParentChain) {
        derivedDirection = when (derivedDirection) {
            Name.ASCENDING -> Name.DESCENDING
            Name.DESCENDING -> Name.ASCENDING
            else -> return Name.UNKNOWN
        }
    }
    return derivedDirection
}

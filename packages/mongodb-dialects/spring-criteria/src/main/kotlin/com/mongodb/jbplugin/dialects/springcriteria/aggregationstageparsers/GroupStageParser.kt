package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.collectTypeUntil
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.parseFieldExpressionAsValueReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveFieldNameFromExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.HasValueReference.ValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

internal const val GROUP_OPERATION_FQN = "org.springframework.data.mongodb.core.aggregation.GroupOperation"
internal const val GROUP_OPERATION_BUILDER_FQN = "$GROUP_OPERATION_FQN.GroupOperationBuilder"

class GroupStageParser : StageParser {
    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ): Boolean {
        return (
            canParse(method) || methodCall.isFieldCreationCallInsideGroupChain()
            ) &&
            method.name != "as"
    }

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val methodFqn = stageCallMethod.containingClass?.qualifiedName
        val isGroupStageCall = methodFqn == AGGREGATE_FQN && stageCallMethod.name == "group"
        return isGroupStageCall ||
            listOf(
                GROUP_OPERATION_FQN,
                GROUP_OPERATION_BUILDER_FQN
            ).contains(methodFqn)
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        val allChainedCalls = stageCall.gatherChainedCalls()
        // In a group stage written in Spring data mongodb, the _id field is specified in the
        // Aggregation.group() call itself. Generally speaking in a list of chained calls the
        // last call is the root group call
        val rootGroupCallWithIdField = allChainedCalls.find { it.isRootGroupCall() }
        val chainedCallsWithoutRootGroupCall = allChainedCalls.filter { !it.isRootGroupCall() }
        val (idFieldReference, idValueReference) =
            parseRootGroupCallToIdFieldAndValueReference(rootGroupCallWithIdField)

        return createGroupStage(
            stageCall = stageCall,
            idFieldReference = idFieldReference,
            idValueReference = idValueReference,
            accumulatedFields = chainedCallsWithoutRootGroupCall.mapIndexedNotNull {
                    index,
                    methodCall
                ->
                val method = methodCall.fuzzyResolveMethod() ?: return@mapIndexedNotNull null
                when (method.name) {
                    "sum", "avg", "first", "last", "max", "min", "push", "addToSet" -> {
                        parseKeyValueAccumulator(
                            operatorMethodCall = methodCall,
                            // The `.as` method call, if at all chained, should be right before this call
                            // in the gathered chain of calls.
                            asMethodCall = allChainedCalls
                                .getOrNull(index - 1)?.takeIf { it.isAsMethodCall() }
                        )
                    }

                    // we explicitly ignore as method call because it is handled as part of parsed
                    // method calls
                    "as" -> null

                    else -> Node(
                        source = methodCall,
                        components = listOf(Named(Name.UNKNOWN))
                    )
                }
            }
        )
    }

    private fun parseRootGroupCallToIdFieldAndValueReference(
        methodCall: PsiMethodCallExpression?
    ): Pair<FieldReference<PsiElement>, ValueReference<PsiElement>> {
        val unknownIdFieldReference = HasFieldReference.Unknown as FieldReference<PsiElement>
        val unknownIdValueReference = HasValueReference.Unknown as ValueReference<PsiElement>
        if (methodCall == null) {
            return unknownIdFieldReference to unknownIdValueReference
        }

        val idFieldReference = HasFieldReference.Inferred(
            source = methodCall as PsiElement,
            fieldName = "_id",
            displayName = "_id",
        )

        val method = methodCall.fuzzyResolveMethod()
            ?: return idFieldReference to unknownIdValueReference

        val fieldStringExpressions = if (method.isVarArgs) {
            methodCall.argumentList.expressions.toList()
        } else {
            val fieldsObjectExpression = methodCall.argumentList.expressions.getOrNull(0)
            fieldsObjectExpression?.resolveFieldStringExpressionsFromFieldsObject() ?: emptyList()
        }

        val schemaFieldReferences = fieldStringExpressions.map { fieldExpression ->
            HasFieldReference(fieldExpression.resolveFieldNameFromExpression())
        }

        val idValueReference = if (schemaFieldReferences.isNotEmpty()) {
            HasValueReference.Computed(
                source = methodCall as PsiElement,
                type = ComputedBsonType(
                    baseType = BsonAny,
                    expression = Node(
                        source = methodCall,
                        components = schemaFieldReferences
                    )
                )
            )
        } else {
            HasValueReference.Constant(
                source = methodCall as PsiElement,
                type = BsonNull,
                value = null
            )
        }

        return idFieldReference to idValueReference
    }

    /**
     * Parses a provided method call into a Node representing the accumulated field using one of the
     * following accumulators: "sum", "avg", "first", "last", "max", "min", "push", "addToSet"
     *
     * A key,value accumulator is generally written as: `.sum("fieldInSchema").as("saveAsField")`
     * Now it might be possible that user is yet to write `.as` part of expression so we treat it
     * as normal case and try to parse the `.sum` part of the expression with our best failsafe
     * option.
     */
    private fun parseKeyValueAccumulator(
        operatorMethodCall: PsiMethodCallExpression,
        asMethodCall: PsiMethodCallExpression?
    ): Node<PsiElement> {
        val fieldExpressionInSumCall = operatorMethodCall.argumentList.expressions.getOrNull(0)
        // The field passed to sum method call could also be an AggregationExpression, which we
        // do not support parsing yet. So we assume that it is a string and try to parse it as
        // a constant and resolve a ValueReference out of it
        val computedValueReference =
            fieldExpressionInSumCall?.parseFieldExpressionAsValueReference()
                ?: HasValueReference(HasValueReference.Unknown)

        val accumulatedFieldExpressionInAsCall =
            asMethodCall?.argumentList?.expressions?.getOrNull(0)
        val accumulatedFieldAsString =
            accumulatedFieldExpressionInAsCall?.tryToResolveAsConstantString()
        val accumulatedFieldReference = HasFieldReference(
            if (accumulatedFieldAsString != null) {
                HasFieldReference.Computed(
                    source = accumulatedFieldExpressionInAsCall,
                    fieldName = accumulatedFieldAsString,
                    displayName = accumulatedFieldAsString
                )
            } else {
                HasFieldReference.Unknown as FieldReference<PsiElement>
            }
        )

        val operatorMethod = operatorMethodCall.fuzzyResolveMethod()
        return Node(
            source = asMethodCall ?: operatorMethodCall,
            listOf(
                Named(
                    when (operatorMethod?.name) {
                        "sum" -> Name.SUM
                        "avg" -> Name.AVG
                        "first" -> Name.FIRST
                        "last" -> Name.LAST
                        "max" -> Name.MAX
                        "min" -> Name.MIN
                        "push" -> Name.PUSH
                        "addToSet" -> Name.ADD_TO_SET
                        else -> Name.UNKNOWN
                    }
                ),
                accumulatedFieldReference,
                computedValueReference,
            )
        )
    }

    private fun createGroupStage(
        stageCall: PsiElement,
        idFieldReference: FieldReference<PsiElement>,
        idValueReference: ValueReference<PsiElement>,
        accumulatedFields: List<Node<PsiElement>>
    ): Node<PsiElement> {
        return Node(
            source = stageCall,
            components = listOf(
                Named(Name.GROUP),
                HasFieldReference(idFieldReference),
                HasValueReference(idValueReference),
                HasAccumulatedFields(accumulatedFields),
            )
        )
    }
}

/**
 * Confirms if a `Field` object creation call was inside one of:
 * 1. AddFieldsOperationBuilder.addFieldWithValueOf()
 * 2. ValueAppender.withValueOf()
 */
fun PsiMethodCallExpression.isFieldCreationCallInsideGroupChain(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    val allParents = method.collectTypeUntil(
        PsiMethodCallExpression::class.java,
        PsiMethod::class.java
    )
    return method.isFieldCreationMethod() &&
        allParents.any { isRootGroupCall() }
}

fun PsiMethodCallExpression.isRootGroupCall(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.name == "group" && method.containingClass?.qualifiedName == AGGREGATE_FQN
}

/**
 * Confirms if the method call represents `GroupOperation.as()` call.
 * Generally available to be chained on expressions such as `.sum("someField").as("asField")`
 */
fun PsiMethodCallExpression.isAsMethodCall(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.name == "as" &&
        method.containingClass?.qualifiedName == GROUP_OPERATION_BUILDER_FQN
}

package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.collectTypeUntil
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAddedFields
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.HasValueReference.ValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.toBsonType

internal const val ADD_FIELDS_OPERATION_FQN = "org.springframework.data.mongodb.core.aggregation.AddFieldsOperation"
internal const val ADD_FIELDS_OPERATION_BUILDER_FQN = "$ADD_FIELDS_OPERATION_FQN.AddFieldsOperationBuilder"
internal const val VALUE_APPENDER_FQN = "$ADD_FIELDS_OPERATION_BUILDER_FQN.ValueAppender"

class AddFieldsStageParser : StageParser {
    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ): Boolean {
        return methodCall.isAddFieldWithValueOfCall() ||
            methodCall.isWithValueOfCall() ||
            methodCall.isFieldCreationCallInsideAddFieldsChain()
    }

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        return listOf(
            AGGREGATE_FQN,
            ADD_FIELDS_OPERATION_FQN,
            ADD_FIELDS_OPERATION_BUILDER_FQN,
            VALUE_APPENDER_FQN
        ).contains(stageCallMethod.containingClass?.qualifiedName) &&
            listOf(
                "addFields",
                "addFieldWithValue",
                "addFieldWithValueOf",
                "addField",
                "withValue",
                "withValueOf",
                "build"
            ).contains(stageCallMethod.name)
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        val allChainedCalls = stageCall.gatherChainedCalls()
        return createAddFieldsNode(
            source = stageCall,
            addedFields = allChainedCalls.mapIndexedNotNull { index, methodCall ->
                val method = methodCall.fuzzyResolveMethod() ?: return@mapIndexedNotNull null
                when (method.name) {
                    // Nothing to parse in these method calls
                    "addFields", "build" -> null
                    // addField contains the field name that is added but that is parsed alongside
                    // the parsing of withValue and withValueOf which is why we ignore it here
                    "addField" -> null
                    "addFieldWithValue" -> parseAddFieldWithValue(methodCall)
                    "addFieldWithValueOf" -> parseAddFieldWithValueOf(methodCall)
                    "withValue" -> parseWithValue(
                        // withValue can only be chained on a ValueAppender instance and thus addField
                        // is expected to be right after the withValue call in the chain
                        addFieldCall = allChainedCalls.getOrNull(index + 1)?.takeIf {
                            it.isAddFieldCall()
                        },
                        withValueCall = methodCall
                    )
                    "withValueOf" -> parseWithValueOf(
                        // withValueOf can only be chained on a ValueAppender instance and thus addField
                        // is expected to be right after the withValue call in the chain
                        addFieldCall = allChainedCalls.getOrNull(index + 1)?.takeIf {
                            it.isAddFieldCall()
                        },
                        withValueCall = methodCall
                    )
                    else -> null
                }
            }
        )
    }

    /**
     * Parses a PsiMethodCallExpression representing withValue() method.
     * withValue() itself accepts a value which can either be a Java primitive type or a reference
     * type and is chained on a ValueAppender instance built using addField() method which accepts
     * a field as a String. To parse withValue() it is imperative to also parse addField() call.
     *
     * Note: Current we support parsing values which are Java primitives and can be resolved during
     * build time.
     */
    private fun parseWithValue(
        addFieldCall: PsiMethodCallExpression?,
        withValueCall: PsiMethodCallExpression
    ): Node<PsiElement> {
        val fieldReference = parseFieldExpression(
            addFieldCall?.argumentList?.expressions?.getOrNull(0)
        )
        val valueReference = parseValueExpressionAsNormalValue(
            withValueCall.argumentList.expressions.getOrNull(0)
        )

        return createAddFieldNode(
            source = withValueCall,
            fieldReference = fieldReference,
            valueReference = valueReference
        )
    }

    /**
     * Parses a PsiMethodCallExpression representing withValueOf() method.
     * withValueOf() accepts a value which could point to:
     * 1. existing field in the database
     * 2. a MongoDB expression that evaluates to some value
     * Note: Currently we support parsing values which points to a field either directly as a String
     * or using a `Field object
     *
     * withValueOf() is chained on a ValueAppender instance built using addField() method which
     * accepts a field as a String. To parse withValue() it is imperative to also parse addField().
     */
    private fun parseWithValueOf(
        addFieldCall: PsiMethodCallExpression?,
        withValueCall: PsiMethodCallExpression
    ): Node<PsiElement> {
        val fieldReference = parseFieldExpression(
            addFieldCall?.argumentList?.expressions?.getOrNull(0)
        )
        val valueReference = parseValueExpressionAsField(
            withValueCall.argumentList.expressions.getOrNull(0)
        )

        return createAddFieldNode(
            source = withValueCall,
            fieldReference = fieldReference,
            valueReference = valueReference
        )
    }

    /**
     * Parses a PsiMethodCallExpression representing addFieldWithValue() method.
     * addFieldWithValue() accepts a field as a String and a value which can either be a Java
     * primitive type or a reference type.
     * Note: Current we support parsing values which are Java primitives and can be resolved during
     * build time.
     */
    private fun parseAddFieldWithValue(methodCall: PsiMethodCallExpression): Node<PsiElement> {
        val fieldReference = parseFieldExpression(
            methodCall.argumentList.expressions.getOrNull(0)
        )
        val valueReference = parseValueExpressionAsNormalValue(
            methodCall.argumentList.expressions.getOrNull(1)
        )

        return createAddFieldNode(
            source = methodCall,
            fieldReference = fieldReference,
            valueReference = valueReference
        )
    }

    /**
     * Parses a PsiMethodCallExpression representing addFieldWithValueOf() method.
     * addFieldWithValueOf() accepts a field as a String and a value which could point to:
     * 1. existing field in the database
     * 2. a MongoDB expression that evaluates to some value
     * Note: Currently we support parsing values which points to a field either directly as a String
     * or using a `Field object
     */
    private fun parseAddFieldWithValueOf(methodCall: PsiMethodCallExpression): Node<PsiElement> {
        val fieldReference = parseFieldExpression(
            methodCall.argumentList.expressions.getOrNull(0)
        )
        val valueReference = parseValueExpressionAsField(
            methodCall.argumentList.expressions.getOrNull(1)
        )

        return createAddFieldNode(
            source = methodCall,
            fieldReference = fieldReference,
            valueReference = valueReference
        )
    }

    private fun parseFieldExpression(fieldExpression: PsiExpression?): FieldReference<PsiElement> {
        val resolvedFieldAddedInCall = fieldExpression?.tryToResolveAsConstantString()
        return if (resolvedFieldAddedInCall == null) {
            HasFieldReference.Unknown as FieldReference<PsiElement>
        } else {
            HasFieldReference.Computed(
                source = fieldExpression,
                fieldName = resolvedFieldAddedInCall,
                displayName = resolvedFieldAddedInCall,
            )
        }
    }

    private fun parseValueExpressionAsNormalValue(valueExpression: PsiExpression?): ValueReference<PsiElement> {
        val (wasResolved, resolvedValue) = valueExpression?.tryToResolveAsConstant()
            ?: (false to null)

        if (!wasResolved || valueExpression == null) {
            return HasValueReference.Unknown as ValueReference<PsiElement>
        }

        return HasValueReference.Constant(
            source = valueExpression,
            value = resolvedValue,
            type = resolvedValue?.javaClass.toBsonType(),
        )
    }

    private fun parseValueExpressionAsField(valueExpression: PsiExpression?): ValueReference<PsiElement> {
        if (valueExpression == null) {
            return HasValueReference.Unknown as ValueReference<PsiElement>
        }

        val fieldExpressionReferencedInValueExpression = if (valueExpression.isFieldObject()) {
            // Since valueExpression is a `Field` object, the value passed to the object is likely
            // a field in mongodb schema so we pluck that out.
            valueExpression.resolveFieldStringExpressionFromFieldObject()
        } else {
            valueExpression
        }
        val resolvedFieldReferencedInValueExpression = fieldExpressionReferencedInValueExpression
            ?.tryToResolveAsConstantString()

        return if (resolvedFieldReferencedInValueExpression == null) {
            HasValueReference.Unknown as ValueReference<PsiElement>
        } else {
            HasValueReference.Computed(
                source = valueExpression,
                type = ComputedBsonType(
                    baseType = BsonAny,
                    expression = Node(
                        source = fieldExpressionReferencedInValueExpression,
                        components = listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(
                                    source = fieldExpressionReferencedInValueExpression,
                                    fieldName = resolvedFieldReferencedInValueExpression.trim('$'),
                                    displayName = "\$${resolvedFieldReferencedInValueExpression.trim(
                                        '$'
                                    )}"
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    private fun createAddFieldNode(
        source: PsiElement,
        fieldReference: FieldReference<PsiElement>,
        valueReference: ValueReference<PsiElement>,
    ): Node<PsiElement> {
        return Node(
            source = source,
            components = listOf(
                Named(Name.ADD_FIELDS),
                HasFieldReference(fieldReference),
                HasValueReference(valueReference)
            )
        )
    }

    private fun createAddFieldsNode(
        source: PsiMethodCallExpression,
        addedFields: List<Node<PsiElement>>
    ): Node<PsiElement> {
        return Node(
            source = source,
            components = listOf(
                Named(Name.ADD_FIELDS),
                HasAddedFields(addedFields)
            )
        )
    }
}

fun PsiMethodCallExpression.isAddFieldWithValueOfCall(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.name == "addFieldWithValueOf" &&
        method.containingClass?.qualifiedName == ADD_FIELDS_OPERATION_BUILDER_FQN
}

fun PsiMethodCallExpression.isWithValueOfCall(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.name == "withValueOf" &&
        method.containingClass?.qualifiedName == VALUE_APPENDER_FQN
}

/**
 * Confirms if a `Field` object creation call was inside one of:
 * 1. AddFieldsOperationBuilder.addFieldWithValueOf()
 * 2. ValueAppender.withValueOf()
 */
fun PsiMethodCallExpression.isFieldCreationCallInsideAddFieldsChain(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    val allParents = method.collectTypeUntil(
        PsiMethodCallExpression::class.java,
        PsiMethod::class.java
    )
    return method.isFieldCreationMethod() &&
        allParents.any {
            isAddFieldWithValueOfCall() || isWithValueOfCall()
        }
}

fun PsiMethodCallExpression.isAddFieldCall(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.name == "addField" &&
        method.containingClass?.qualifiedName == ADD_FIELDS_OPERATION_BUILDER_FQN
}

/**
 * Confirms if a PsiExpression represents a `Field` object
 */
fun PsiExpression.isFieldObject(): Boolean {
    return resolveToMethodCallExpression { _, method ->
        method.isFieldCreationMethod()
    } != null
}

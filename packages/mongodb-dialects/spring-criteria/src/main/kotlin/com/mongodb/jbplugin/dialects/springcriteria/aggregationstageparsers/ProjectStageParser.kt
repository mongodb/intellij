package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveFieldNameFromExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.dialects.springcriteria.FIELDS_FQN
import com.mongodb.jbplugin.dialects.springcriteria.PROJECTION_OPERATION_FQN
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

class ProjectStageParser : StageParser {
    private fun createProjectNode(
        source: PsiElement,
        projections: List<Node<PsiElement>>
    ): Node<PsiElement> {
        return Node(
            source = source,
            components = listOf(
                Named(Name.PROJECT),
                HasProjections(
                    children = projections
                )
            )
        )
    }

    private fun createProjectedFieldNode(
        fieldExpression: PsiExpression,
        projectionName: Name
    ): Node<PsiElement> {
        val fieldReference = fieldExpression.resolveFieldNameFromExpression()
        return Node(
            source = fieldExpression as PsiElement,
            components = listOf(
                Named(projectionName),
                HasFieldReference(fieldReference),
                HasValueReference(
                    reference = when (projectionName) {
                        Name.INCLUDE,
                        Name.EXCLUDE -> HasValueReference.Inferred(
                            source = fieldExpression,
                            value = if (projectionName == Name.INCLUDE) 1 else 0,
                            type = BsonInt32
                        )
                        else -> HasValueReference.Unknown
                    }
                )
            )
        )
    }

    /**
     * Parses a `PsiExpression` that represents a `Field` object and returns a `Node` for the field
     * referenced in the `Field` object.
     *
     * The only helper that we support for creating a `Field` object is `Fields.field()` with the
     * following alternative signatures:
     * 1. `Fields.field("fieldA")`
     * 2. `Fields.field(fieldBFromVariable)`
     * 3. `Fields.field(fieldCFromMethodCall())`
     */
    private fun fieldObjectExpressionToProjectedFieldNode(
        fieldObjectExpression: PsiExpression,
        projectionName: Name
    ): Node<PsiElement>? {
        val resolvedFieldMethodCall = fieldObjectExpression.resolveToMethodCallExpression {
                _,
                fieldObjectMethod
            ->
            fieldObjectMethod.containingClass?.qualifiedName == FIELDS_FQN &&
                fieldObjectMethod.name == "field"
        } ?: return null

        val resolvedFieldMethod = resolvedFieldMethodCall.fuzzyResolveMethod() ?: return null
        // This represents the following call:
        // `Fields.field("fieldAlias", "actualFieldInDocument")`
        // and since this translates to a rename operation, which we do not support just yet, we
        // will ignore this until we come back to this.
        if (resolvedFieldMethodCall.argumentList.expressions.size == 2) {
            return null
        } else {
            val fieldExpression = resolvedFieldMethodCall.argumentList.expressions.getOrNull(0)
                ?: return null
            return createProjectedFieldNode(fieldExpression, projectionName)
        }
    }

    /**
     * Parses a `PsiExpression` that represents a `Fields` object and returns a `Node` for each
     * field referenced in the `Fields` object.
     *
     * The two different ways of generating a `Fields` object that we support and parse in here are:
     * 1. `Fields.fields("fieldA", fieldBFromVariable, fieldCFromMethodCall())`
     * 2. `Fields.from(Fields.field("fieldA"), Fields.field(fieldBFromVariable))`
     */
    private fun fieldsObjectExpressionToProjectedFieldNode(
        fieldsObjectExpression: PsiExpression,
        projectionName: Name
    ): List<Node<PsiElement>> {
        val resolvedFieldsMethodCall = fieldsObjectExpression.resolveToMethodCallExpression {
                _,
                fieldsObjectMethod
            ->
            fieldsObjectMethod.containingClass?.qualifiedName == FIELDS_FQN &&
                (fieldsObjectMethod.name == "fields" || fieldsObjectMethod.name == "from")
        } ?: return emptyList()

        val resolvedFieldsMethod = resolvedFieldsMethodCall.fuzzyResolveMethod()
            ?: return emptyList()

        return when (resolvedFieldsMethod.name) {
            "fields" -> resolvedFieldsMethodCall.argumentList.expressions.mapNotNull {
                createProjectedFieldNode(it, projectionName)
            }
            "from" -> resolvedFieldsMethodCall.argumentList.expressions.mapNotNull {
                fieldObjectExpressionToProjectedFieldNode(it, projectionName)
            }
            else -> {
                // log.warn("Unsupported Fields method: ${resolvedFieldsMethod.name}")
                emptyList()
            }
        }
    }

    /**
     * Parses a method that has the following overloaded signatures:
     * 1. methodCall(String... fieldNames)
     * 2. methodCall(Fields fields)
     */
    private fun parseMethodCallWithStringVarArgsAndFields(
        methodCall: PsiMethodCallExpression,
        projectionName: Name,
    ): List<Node<PsiElement>> {
        val method = methodCall.fuzzyResolveMethod() ?: return emptyList()
        return if (method.isVarArgs) {
            methodCall.argumentList.expressions.mapNotNull {
                createProjectedFieldNode(fieldExpression = it, projectionName = projectionName)
            }
        } else {
            val maybeFieldsObjectExpression = methodCall.argumentList.expressions.getOrNull(0)
                ?: return emptyList()
            fieldsObjectExpressionToProjectedFieldNode(maybeFieldsObjectExpression, projectionName)
        }
    }

    private fun parseAndIncludeCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        // andInclude have two overloads, one which takes string varargs and another
        // which takes a Fields object
        return parseMethodCallWithStringVarArgsAndFields(methodCall, Name.INCLUDE)
    }

    private fun parseAndExcludeCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        // andExclude only accepts a varargs of strings as arguments
        return methodCall.argumentList.expressions.mapNotNull {
            createProjectedFieldNode(fieldExpression = it, projectionName = Name.EXCLUDE)
        }
    }

    private fun parseProjectCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        // Aggregation.project() has three overloads
        // 1. Aggregation.project(String... fieldNames)
        // 2. Aggregation.project(Fields fields)
        // 3. Aggregation.project(Class<?> type)
        // We only support the first 2 and ignore the third one
        return parseMethodCallWithStringVarArgsAndFields(methodCall, Name.INCLUDE)
    }

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val methodFqn = stageCallMethod.containingClass?.qualifiedName ?: return false
        // Project stage call might contain chained operations which might result
        // in a method call from `PROJECTION_OPERATION_FQN` so we account for both
        return listOf(AGGREGATE_FQN, PROJECTION_OPERATION_FQN).contains(methodFqn) &&
            // we consider the root call project and any chained calls which we currently
            // support
            listOf("project", "andInclude", "andExclude").contains(stageCallMethod.name)
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        return createProjectNode(
            source = stageCall,
            projections = stageCall.gatherChainedCalls().flatMap { methodCall ->
                val method = methodCall.fuzzyResolveMethod() ?: return@flatMap emptyList()
                when (method.name) {
                    "andInclude" -> parseAndIncludeCall(methodCall)
                    "andExclude" -> parseAndExcludeCall(methodCall)
                    "project" -> parseProjectCall(methodCall)
                    else -> emptyList()
                }
            }
        )
    }
}

/**
 * From a PsiMethodCallExpression, it attempts to travel upwards the chain(assuming there is one)
 * while gathering other PsiMethodCallExpressions it comes across, until there is no further
 * PsiMethodCallExpression in the chain. For example, consider the following method call
 * ```
 * Aggregation.project().andInclude("fieldA").andExclude("_id")
 * ```
 * and given that the method was called for PsiMethodCallExpression referring the entire call above,
 * the chain will consist of the following method calls
 * [
 *   Aggregation.project().andInclude("fieldA").andExclude("_id"),
 *   Aggregation.project().andInclude("fieldA")
 *   Aggregation.project()
 * ]
 */
fun PsiMethodCallExpression.gatherChainedCalls(): List<PsiMethodCallExpression> {
    val chain = mutableListOf<PsiMethodCallExpression>()
    var currentCall: PsiMethodCallExpression? = this

    while (currentCall != null) {
        chain.add(currentCall)
        currentCall = currentCall.methodExpression.qualifierExpression as? PsiMethodCallExpression
    }

    return chain
}

package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveFieldNameFromExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.resolveToMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.dialects.springcriteria.ARRAY_PROJECTION_OPERATION_BUILDER_FQN
import com.mongodb.jbplugin.dialects.springcriteria.FIELDS_FQN
import com.mongodb.jbplugin.dialects.springcriteria.PROJECTION_OPERATION_BUILDER_FQN
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
        return fieldsObjectExpression.resolveFieldStringExpressionsFromFieldsObject()
            .map { fieldStringExpression ->
                createProjectedFieldNode(fieldStringExpression, projectionName)
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
            methodCall.argumentList.expressions.map {
                createProjectedFieldNode(fieldExpression = it, projectionName = projectionName)
            }
        } else {
            val maybeFieldsObjectExpression = methodCall.argumentList.expressions.getOrNull(0)
                ?: return emptyList()
            fieldsObjectExpressionToProjectedFieldNode(maybeFieldsObjectExpression, projectionName)
        }
    }

    private fun parseAndIncludeCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        // andInclude has two overloads, one which takes string varargs and another
        // which takes a Fields object
        return parseMethodCallWithStringVarArgsAndFields(methodCall, Name.INCLUDE)
    }

    private fun parseAndExcludeCall(methodCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        // andExclude only accepts a varargs of strings as arguments
        return methodCall.argumentList.expressions.map {
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

    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ): Boolean {
        val methodFqn = method.containingClass?.qualifiedName
        // Autocomplete for Aggregation.project("<caret>")
        return (
            methodFqn == AGGREGATE_FQN &&
                method.name == "project"
            ) ||
            // Autocomplete for Aggregation.project().andInclude("<caret>")
            (
                methodFqn == PROJECTION_OPERATION_FQN &&
                    (method.name == "andInclude" || method.name == "andExclude")
                ) ||
            // Autocomplete for Aggregation.project(Fields.fields("<caret>")) or,
            // Aggregation.project(Fields.from(Fields.field("<caret>")))
            (
                methodFqn == FIELDS_FQN &&
                    (method.name == "fields" || method.name == "field")
                )
    }

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val methodFqn = stageCallMethod.containingClass?.qualifiedName ?: return false
        val isProjectStageCall = methodFqn == AGGREGATE_FQN && stageCallMethod.name == "project"
        // Project stage call might contain chained operations which might result
        // in a method call from `PROJECTION_OPERATION_FQN` so we account for both
        return isProjectStageCall ||
            listOf(
                PROJECTION_OPERATION_FQN,
                PROJECTION_OPERATION_BUILDER_FQN,
                ARRAY_PROJECTION_OPERATION_BUILDER_FQN,
            ).contains(methodFqn)
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
                    else -> listOf(
                        Node(
                            source = methodCall,
                            components = listOf(Named(Name.UNKNOWN))
                        )
                    )
                }
            }
        )
    }
}

/**
 * Parses a `PsiExpression` that represents a `Fields` object and returns a `PsiExpression` for each
 * field referenced in the `Fields` object.
 *
 * The two different ways of generating a `Fields` object that we support and parse in here are:
 * 1. `Fields.fields("fieldA", stringFieldFromVariable, stringFieldFromMethodCall())`
 * 2. `Fields.from(Fields.field("fieldA"), Fields.field(stringFieldFromVariable))`
 */
fun PsiExpression.resolveFieldStringExpressionsFromFieldsObject(): List<PsiExpression> {
    val resolvedFieldsMethodCall = resolveToMethodCallExpression {
            _,
            fieldsObjectMethod
        ->
        fieldsObjectMethod.containingClass?.qualifiedName == FIELDS_FQN &&
            (fieldsObjectMethod.name == "fields" || fieldsObjectMethod.name == "from")
    } ?: return emptyList()

    val resolvedFieldsMethod = resolvedFieldsMethodCall.fuzzyResolveMethod()
        ?: return emptyList()

    return when (resolvedFieldsMethod.name) {
        // Fields.fields("fieldA", "fieldB")
        "fields" -> resolvedFieldsMethodCall.argumentList.expressions.toList()
        // Fields.from(Fields.field("fieldA"), Fields.field("fieldB"))
        "from" -> resolvedFieldsMethodCall.argumentList.expressions.mapNotNull {
            it.resolveFieldStringExpressionFromFieldObject()
        }
        else -> {
            // log.warn("Unsupported Fields method: ${resolvedFieldsMethod.name}")
            emptyList()
        }
    }
}

/**
 * Parses a `PsiExpression` that represents a `Field` object and returns a `PsiExpression` for the
 * field referenced in the `Field` object.
 *
 * The only helper that we support for creating a `Field` object is `Fields.field()` with the
 * following alternative signatures:
 * 1. `Fields.field("fieldA")`
 * 2. `Fields.field(fieldBFromVariable)`
 * 3. `Fields.field(fieldCFromMethodCall())`
 */
fun PsiExpression.resolveFieldStringExpressionFromFieldObject(): PsiExpression? {
    val resolvedFieldMethodCall = resolveToMethodCallExpression {
            _,
            fieldObjectMethod
        ->
        fieldObjectMethod.isFieldCreationMethod()
    } ?: return null

    // This represents the following call:
    // `Fields.field("fieldAlias", "actualFieldInDocument")`
    // and since this translates to a rename operation, which we do not support just yet, we
    // will ignore this until we come back to this.
    return if (resolvedFieldMethodCall.argumentList.expressions.size == 2) {
        null
    } else {
        resolvedFieldMethodCall.argumentList.expressions.getOrNull(0)
    }
}

fun PsiMethod.isFieldCreationMethod(): Boolean {
    return containingClass?.qualifiedName == FIELDS_FQN && name == "field"
}

/**
 * From a PsiMethodCallExpression, it attempts to travel backwards the chain(assuming there is one)
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
 *
 * Note: The collected chain of method calls always starts from the method call all the way to the
 * right of the chain and ends with the root method call.
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

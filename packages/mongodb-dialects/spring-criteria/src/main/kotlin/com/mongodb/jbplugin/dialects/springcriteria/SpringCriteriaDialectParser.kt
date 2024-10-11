package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.*
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

private const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"
private const val DOCUMENT_FQN = "org.springframework.data.mongodb.core.mapping.Document"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) =
        source.findCriteriaWhereExpression() != null

    override fun attachment(source: PsiElement): PsiElement = source.findCriteriaWhereExpression()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiExpression) {
            return Node(source, emptyList())
        }

        val criteriaChain = source.findCriteriaWhereExpression() ?: return Node(source, emptyList())
        val targetCollection = QueryTargetCollectionExtractor.extractCollection(source)

        val mongoOpCall = criteriaChain.parentMongoDbOperation() ?: return Node(source, emptyList())
        val mongoOpMethod =
            mongoOpCall.fuzzyResolveMethod() ?: return Node(mongoOpCall, emptyList())

        val command = inferCommandFromMethod(mongoOpMethod)

        // not all methods work the same way (sigh) so we will need a big `when` to handle
        // each special case
        return when (mongoOpMethod.name) {
            "count",
            "exactCount",
            "exists",
            "find",
            "findAll",
            "findAllAndRemove",
            "findAndRemove",
            "findOne",
            "scroll",
            "stream" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseQueryRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                    )
                )
            )
            "findAndModify",
            "findAndReplace",
            "updateFirst",
            "updateMulti",
            "upsert" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "findById" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "findDistinct" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "insert" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "insertAll" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "remove" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "replace" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            else -> Node(
                mongoOpCall,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(parseQueryRecursively(criteriaChain))
                )
            )
        }
        return Node(
            mongoOpCall,
            listOf(
                command,
                targetCollection,
                HasFilter(parseQueryRecursively(criteriaChain))
            )
        )
    }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        return false // databases are in property files and we don't support AC there yet
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        val docAnnotation = source.parentOfType<PsiAnnotation>() ?: return false
        return docAnnotation.hasQualifiedName(DOCUMENT_FQN)
    }

    override fun isReferenceToField(source: PsiElement): Boolean {
        val isString =
            source.parentOfType<PsiLiteralExpression>(true)?.tryToResolveAsConstantString() != null
        val methodCall = source.parentOfType<PsiMethodCallExpression>() ?: return false

        /*
         * IntelliJ might detect that we are not in a string, but in a whitespace or a dot due to, probably,
         * some internal race conditions. In this case, we will check the parent, which will be an ExpressionList, that
         * will contain all tokens and the string we actually want. In case it's a dot, we are here:
         * where(). <--
         * So we need to check the previous sibling to find if we are in a criteria expression.
         */
        if (source is PsiWhiteSpace ||
            (source is PsiJavaToken && source.elementType?.toString() != "STRING_LITERAL")
        ) {
            val parentExpressionList = source.parent
            val siblingAsMethodCall = source.prevSibling as? PsiMethodCallExpression ?: return false

            return siblingAsMethodCall.isCriteriaExpression() ||
                parentExpressionList.children.filterIsInstance<PsiExpression>().any {
                    isReferenceToField(it)
                }
        }

        return isString && methodCall.isCriteriaExpression()
    }

    private fun parseQueryRecursively(
        fieldNameCall: PsiElement?,
        until: PsiElement? = null
    ): List<Node<PsiElement>> {
        if (fieldNameCall == null) {
            return emptyList()
        }

        fieldNameCall as PsiMethodCallExpression

        val valueCall = fieldNameCall.parentMethodCallExpression() ?: return emptyList()

        if (!fieldNameCall.isCriteriaQueryMethod() ||
            fieldNameCall == until ||
            valueCall == until
        ) {
            return emptyList()
        }

        val currentCriteriaMethod = fieldNameCall.fuzzyResolveMethod() ?: return emptyList()
        if (currentCriteriaMethod.isVarArgs) {
            val allSubQueries = fieldNameCall.argumentList.expressions
                .filterIsInstance<PsiMethodCallExpression>()
                .map { it.innerMethodCallExpression() }
                .flatMap { parseQueryRecursively(it, fieldNameCall) }

            if (fieldNameCall.parent.parent is PsiMethodCallExpression) {
                val named = operatorName(currentCriteriaMethod)
                val nextField = fieldNameCall.parent.parent as PsiMethodCallExpression
                return listOf(
                    Node<PsiElement>(fieldNameCall, listOf(named, HasFilter(allSubQueries)))
                ) +
                    parseQueryRecursively(nextField, until)
            }
        }

        if (fieldNameCall.argumentList.expressions.isEmpty()) {
            return emptyList()
        }

        val fieldName = fieldNameCall.argumentList.expressions[0].tryToResolveAsConstantString()!!
        val (isResolved, value) = valueCall.argumentList.expressions[0].tryToResolveAsConstant()
        val name = valueCall.fuzzyResolveMethod()?.name!!

        val fieldReference = HasFieldReference(
            HasFieldReference.Known(fieldNameCall.argumentList.expressions[0], fieldName)
        )

        val valueReference = HasValueReference(
            if (isResolved) {
                HasValueReference.Constant(valueCall, value, value!!.javaClass.toBsonType(value))
            } else {
                HasValueReference.Runtime(
                    valueCall,
                    valueCall.argumentList.expressions[0].type?.toBsonType() ?: BsonAny
                )
            }
        )

        val predicate = Node<PsiElement>(
            fieldNameCall,
            listOf(
                Named(name.toName()),
                fieldReference,
                valueReference
            )
        )

        valueCall.parentMethodCallExpression()?.let {
            return listOf(predicate) + parseQueryRecursively(it, until)
        }

        return listOf(predicate)
    }

    private fun operatorName(currentCriteriaMethod: PsiMethod): Named {
        val name = currentCriteriaMethod.name.replace("Operator", "")
        val named = Named(name.toName())
        return named
    }

    /**
     * List of methods from here:
     * https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/core/MongoOperations.html
     */
    private fun inferCommandFromMethod(mongoOpMethod: PsiMethod): IsCommand {
        return IsCommand(
            when (mongoOpMethod.name) {
                "aggregate", "aggregateStream" -> IsCommand.CommandType.AGGREGATE
                "count", "exactCount" -> IsCommand.CommandType.COUNT_DOCUMENTS
                "estimatedCount" -> IsCommand.CommandType.ESTIMATED_DOCUMENT_COUNT
                "exists" -> IsCommand.CommandType.FIND_ONE
                "find", "findAll" -> IsCommand.CommandType.FIND_MANY
                "findDistinct" -> IsCommand.CommandType.DISTINCT
                "findAllAndRemove" -> IsCommand.CommandType.DELETE_MANY
                "findAndModify" -> IsCommand.CommandType.FIND_ONE_AND_UPDATE
                "findAndRemove" -> IsCommand.CommandType.FIND_ONE_AND_DELETE
                "findAndReplace" -> IsCommand.CommandType.FIND_ONE_AND_REPLACE
                "findById" -> IsCommand.CommandType.FIND_ONE
                "insert" -> IsCommand.CommandType.INSERT_ONE
                "insertAll" -> IsCommand.CommandType.INSERT_MANY
                "remove" -> IsCommand.CommandType.DELETE_MANY
                "replace" -> IsCommand.CommandType.REPLACE_ONE
                "save" -> IsCommand.CommandType.UPSERT
                "scroll", "stream" -> IsCommand.CommandType.FIND_MANY
                "updateFirst" -> IsCommand.CommandType.UPDATE_ONE
                "updateMulti" -> IsCommand.CommandType.UPDATE_MANY
                "upsert" -> IsCommand.CommandType.UPSERT
                "one", "oneValue", "first", "firstValue" -> IsCommand.CommandType.FIND_ONE
                "all" -> IsCommand.CommandType.FIND_MANY
                else -> IsCommand.CommandType.UNKNOWN
            }
        )
    }
}

/**
 * Returns whether the current method is a criteria method.
 *
 * @return
 */
fun PsiMethodCallExpression.isCriteriaExpression(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}

private fun PsiElement.findCriteriaWhereExpression(): PsiMethodCallExpression? {
    val parentStatement = PsiTreeUtil.getParentOfType(this, PsiStatement::class.java) ?: return null
    val methodCalls = parentStatement.findAllChildrenOfType(PsiMethodCallExpression::class.java)
    var bottomLevel: PsiMethodCallExpression = methodCalls.find { methodCall ->
        val method = methodCall.fuzzyResolveMethod() ?: return@find false
        method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN &&
            method.name == "where"
    } ?: return null

    while (bottomLevel.text.startsWith("where")) {
        bottomLevel = (bottomLevel.parent as? PsiMethodCallExpression) ?: return bottomLevel
    }

    return bottomLevel
}

private fun PsiMethodCallExpression.isCriteriaQueryMethod(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}

private fun PsiMethodCallExpression.parentMethodCallExpression(): PsiMethodCallExpression? {
    // In this function, we have an expression similar to:
    // a().b()
    // ^  ^ ^ this is B, the current method call expression
    // |  | this is one parent (a reference to the current method)
    // | this is parent.parent (the previous call expression)
    return parent.parent as? PsiMethodCallExpression
}

private fun PsiMethodCallExpression.innerMethodCallExpression(): PsiMethodCallExpression {
    // Navigates downwards until the end of the query chain:
    // a().b()
    // ^   ^ ^
    // |   | this is children[0].children[0]
    // |   | this is children[0]
    // | this is the current method call expression
    // we do it recursively because there is an indeterminate amount of chains
    var ref = this
    while (isCriteriaQueryMethod()) {
        val next = ref.children[0].children[0] as? PsiMethodCallExpression ?: return ref
        ref = next
    }

    return ref
}

private fun PsiMethodCallExpression.parentMongoDbOperation(): PsiMethodCallExpression? {
    var parentMethodCall = findParentOfType<PsiMethodCallExpression>() ?: return null
    val method = parentMethodCall.fuzzyResolveMethod() ?: return null

    if (INTERFACES_WITH_QUERY_METHODS.any {
            method.containingClass?.qualifiedName?.contains(it) ==
                true
        }
    ) {
        return parentMethodCall.parentMongoDbOperation() ?: parentMethodCall
    }

    return parentMethodCall.parentMongoDbOperation()
}

private fun String.toName(): Name = when (this) {
    "is" -> Name.EQ
    else -> Name.from(this)
}

/**
 * As MongoOperations <b>implement a ton</b> of interfaces, we need to check in which one
 * we've found the method:
 *
 * https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/core/MongoOperations.html
 *
 * We are not going to support mapReduce as they are deprecated in MongoDB.
 */
private val INTERFACES_WITH_QUERY_METHODS = arrayOf(
    "MongoTemplate",
    "MongoOperations",
    "ExecutableAggregationOperation",
    "ExecutableFindOperation",
    "ExecutableInsertOperation",
    "ExecutableMapReduceOperation",
    "ExecutableRemoveOperation",
    "ExecutableUpdateOperation",
    "FluentMongoOperations",
    "ExecutableAggregationOperation",
    "ExecutableFindOperation",
    "ExecutableInsertOperation",
    "ExecutableRemoveOperation",
    "ExecutableUpdateOperation",
)

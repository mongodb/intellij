package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.findTopParentBy
import com.mongodb.jbplugin.dialects.javadriver.glossary.findUsageSite
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.getHostReferenceOrNull
import com.mongodb.jbplugin.dialects.javadriver.glossary.inferFromSingleArrayArgument
import com.mongodb.jbplugin.dialects.javadriver.glossary.inferFromSingleVarArgElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.inferValueReferenceFromVarArg
import com.mongodb.jbplugin.dialects.javadriver.glossary.isArray
import com.mongodb.jbplugin.dialects.javadriver.glossary.isJavaIterable
import com.mongodb.jbplugin.dialects.javadriver.glossary.meaningfulExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.dialects.springcriteria.QueryTargetCollectionExtractor.extractCollectionFromClassTypeParameter
import com.mongodb.jbplugin.dialects.springcriteria.QueryTargetCollectionExtractor.extractCollectionFromQueryChain
import com.mongodb.jbplugin.dialects.springcriteria.QueryTargetCollectionExtractor.extractCollectionFromStringTypeParameter
import com.mongodb.jbplugin.dialects.springcriteria.QueryTargetCollectionExtractor.or
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.AddFieldsStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.GroupStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.LimitStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.MatchStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.ProjectStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.SortStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.StageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.UnwindStageParser
import com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers.gatherChainedCalls
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.toBsonType

internal const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"
internal const val QUERY_CLASS_FQN = "org.springframework.data.mongodb.core.query.Query"
internal const val DOCUMENT_FQN = "org.springframework.data.mongodb.core.mapping.Document"
internal const val MONGO_TEMPLATE_FQN = "org.springframework.data.mongodb.core.MongoTemplate"
internal const val AGGREGATE_FQN = "org.springframework.data.mongodb.core.aggregation.Aggregation"
internal const val PROJECTION_OPERATION_FQN = "org.springframework.data.mongodb.core.aggregation.ProjectionOperation"
internal const val PROJECTION_OPERATION_BUILDER_FQN = "$PROJECTION_OPERATION_FQN.ProjectionOperationBuilder"
internal const val ARRAY_PROJECTION_OPERATION_BUILDER_FQN = "$PROJECTION_OPERATION_FQN.ArrayProjectionOperationBuilder"
internal const val FIELDS_FQN = "org.springframework.data.mongodb.core.aggregation.Fields"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    private val aggregationStageParsers: List<StageParser> = listOf(
        MatchStageParser(::parseFilterRecursively),
        ProjectStageParser(),
        UnwindStageParser(),
        SortStageParser(),
        AddFieldsStageParser(),
        GroupStageParser(),
        LimitStageParser(),
    )

    override fun attachment(source: PsiElement): PsiElement? = source.findTopParentBy {
        inferCommandFromMethod(
            (it as? PsiMethodCallExpression)?.fuzzyResolveMethod()
        ).type != IsCommand.CommandType.UNKNOWN
    } ?: attachmentFromUsagePoint(source)

    private fun attachmentFromUsagePoint(source: PsiElement): PsiElement? {
        val usageSite = source.getHostReferenceOrNull()?.findUsageSite() ?: source
        return usageSite.findTopParentBy {
            inferCommandFromMethod(
                (it.findUsageSite() as? PsiMethodCallExpression)?.fuzzyResolveMethod()
            ).type != IsCommand.CommandType.UNKNOWN
        }
    }

    override fun parseCollectionReference(source: PsiElement): HasCollectionReference<PsiElement> {
        val methodCallExpression = source.parentOfType<PsiMethodCallExpression>(withSelf = true)
        val maybeMongoOpCall = methodCallExpression?.findSpringMongoDbExpression()
        val actualMethod = maybeMongoOpCall?.firstChild?.firstChild as? PsiMethodCallExpression
        return extractCollectionFromQueryChain(maybeMongoOpCall).or(
            extractCollectionFromClassTypeParameter(
                actualMethod?.argumentList?.expressions?.getOrNull(1)
            )
        )
    }

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiMethodCallExpression) {
            return Node(source, emptyList())
        }

        val sourceDialect = HasSourceDialect(HasSourceDialect.DialectName.SPRING_CRITERIA)

        val mongoOpCall = source.findSpringMongoDbExpression()
        val mongoOpMethod = mongoOpCall?.fuzzyResolveMethod()

        val inferredFromChain = extractCollectionFromQueryChain(mongoOpCall)
        val command = inferCommandFromMethod(mongoOpMethod)

        // not all methods work the same way (sigh) so we will need a big `when` to handle
        // each special case
        return when (mongoOpMethod?.name) {
            "matching",
            "all",
            "first",
            "firstValue",
            "one",
            "oneValue" -> {
                // these are terminal operators, so the query is just above us (below in the PSI)
                val actualMethod = mongoOpCall.firstChild?.firstChild as? PsiMethodCallExpression
                    ?: return Node(mongoOpCall, listOf(sourceDialect, command, inferredFromChain))

                val filters = actualMethod.argumentList.expressions.getOrNull(0)
                    ?.meaningfulExpression() as? PsiExpression

                return Node(
                    actualMethod,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                actualMethod.argumentList.expressions.getOrNull(1)
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters).reversed()
                        ),
                        HasSorts(
                            QuerySortParser().parse(filters)
                        ),
                    ) + (parseLimitCall(filters)?.let { listOf(it) } ?: emptyList())
                )
            }
            "count",
            "exactCount",
            "exists",
            "find",
            "findAll",
            "findAllAndRemove",
            "findAndRemove",
            "findOne",
            "scroll",
            "stream" -> {
                val filters = mongoOpCall.argumentList.expressions.getOrNull(0)
                    ?.meaningfulExpression() as? PsiExpression

                Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)
                                    ?.meaningfulExpression() as? PsiExpression
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters).reversed()
                        ),
                        HasSorts(
                            QuerySortParser().parse(filters)
                        )
                    ) + (parseLimitCall(filters)?.let { listOf(it) } ?: emptyList())
                )
            }
            "findAndModify",
            "findAndReplace",
            "update",
            "updateFirst",
            "updateMulti",
            "upsert" -> {
                val filters = mongoOpCall.argumentList.expressions.getOrNull(0)
                    ?.meaningfulExpression() as? PsiExpression

                val updates = mongoOpCall.argumentList.expressions.getOrNull(1)
                    ?.meaningfulExpression() as? PsiExpression

                Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                            )
                        ).or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(2)?.meaningfulExpression() as? PsiExpression
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters)
                                .reversed()
                        ),
                        HasSorts(
                            QuerySortParser().parse(filters)
                        ),
                        HasUpdates(
                            parseUpdateRecursively(
                                updates
                            )
                                .reversed()
                        )
                    ) + (parseLimitCall(filters)?.let { listOf(it) } ?: emptyList())
                )
            }
            "findById" -> Node(
                mongoOpCall,
                listOf(
                    sourceDialect,
                    command,
                    inferredFromChain.or(
                        extractCollectionFromClassTypeParameter(
                            mongoOpCall.argumentList.expressions.getOrNull(1)
                        )
                    ),
                    HasFilter(parseFindById(mongoOpCall.argumentList.expressions.getOrNull(0)))
                )
            )
            "findDistinct" -> {
                val filters = mongoOpCall.argumentList.expressions.getOrNull(0)?.meaningfulExpression() as? PsiExpression
                Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters)
                                .reversed()
                        ),
                        HasSorts(
                            QuerySortParser().parse(filters)
                        )
                    ) + (parseLimitCall(filters)?.let { listOf(it) } ?: emptyList())
                )
            }
            "insert" -> Node(
                mongoOpCall,
                listOf(
                    sourceDialect,
                    command,
                    inferredFromChain.or(
                        extractCollectionFromClassTypeParameter(
                            mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                        )
                    ),
                )
            )
            "insertAll" -> Node(
                mongoOpCall,
                listOf(
                    sourceDialect,
                    command,
                    inferredFromChain.or(
                        extractCollectionFromClassTypeParameter(
                            mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                        )
                    ),
                )
            )
            "remove" -> {
                val filters = mongoOpCall.argumentList.expressions.getOrNull(0)?.meaningfulExpression() as? PsiExpression
                Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters)
                                .reversed()
                        ),
                        HasSorts(QuerySortParser().parse(filters))
                    )
                )
            }
            "replace" -> {
                val filters = mongoOpCall.argumentList.expressions.getOrNull(0)?.meaningfulExpression() as? PsiExpression
                Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters)
                                .reversed()
                        ),
                        HasSorts(QuerySortParser().parse(filters))
                    )
                )
            }
            "aggregate", "aggregateStream" -> {
                val expressions = mongoOpCall.argumentList.expressions
                val collectionExpression = expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                return Node(
                    mongoOpCall,
                    listOf(
                        sourceDialect,
                        command,
                        // MongoTemplate.aggregate accepts both Class and a string for specifying the
                        // collection where the aggregation will run so we need to account for both
                        // method signatures while extracting the collection
                        //
                        // Note: It is uncommon to have the class type parameter referenced as a variable
                        // which is why we don't attempt to resolve the parameter as ClassType
                        extractCollectionFromClassTypeParameter(collectionExpression).or(
                            extractCollectionFromStringTypeParameter(collectionExpression)
                        ),
                        HasAggregation(
                            children = AggregationStagesParser(
                                stageParsers = aggregationStageParsers
                            ).parse(mongoOpCall)
                        )
                    )
                )
            }
            else -> {
                val filters = mongoOpCall?.argumentList?.expressions?.getOrNull(0)
                Node(
                    mongoOpCall!!,
                    listOf(
                        sourceDialect,
                        command,
                        inferredFromChain.or(
                            extractCollectionFromClassTypeParameter(
                                mongoOpCall.argumentList.expressions.getOrNull(1)?.meaningfulExpression() as? PsiExpression
                            )
                        ),
                        HasFilter(
                            parseFilterRecursively(filters)
                        )
                    ) + (parseLimitCall(filters)?.let { listOf(it) } ?: emptyList())
                )
            }
        }
    }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        return false // databases are in property files, and we don't support AC there yet
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        return isInsideDocAnnotations(source) || isInsideOneOfAggregationFactoryMethod(source)
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

        return isString &&
            (
                methodCall.isCriteriaExpression() ||
                    methodCall.isSuitableForFieldAutoCompleteInAggregation(aggregationStageParsers)
                )
    }

    private fun isInsideDocAnnotations(source: PsiElement): Boolean {
        val docAnnotation = source.parentOfType<PsiAnnotation>() ?: return false
        return docAnnotation.hasQualifiedName(DOCUMENT_FQN)
    }

    private fun isInsideOneOfAggregationFactoryMethod(source: PsiElement): Boolean {
        val maybeNewAggregationCall = source.parentOfType<PsiMethodCallExpression>() ?: return false
        val methodCall = maybeNewAggregationCall.fuzzyResolveMethod() ?: return false
        return methodCall.containingClass?.qualifiedName == MONGO_TEMPLATE_FQN &&
            (methodCall.name == "aggregate" || methodCall.name == "aggregateStream")
    }

    private fun parseFindById(
        valueFilterExpression: PsiElement?
    ): List<Node<PsiElement>> {
        if (valueFilterExpression == null) return emptyList()

        // basically, it's template.findById(id)
        // so we need to generate a value from the expression
        return listOf(
            Node(
                valueFilterExpression,
                listOf(
                    Named(Name.EQ),
                    HasFieldReference(HasFieldReference.FromSchema(valueFilterExpression, "_id")),
                    psiExpressionToValueReference(valueFilterExpression as? PsiExpression)
                )
            )
        )
    }

    private fun parseFilterRecursively(
        valueFilterExpression: PsiElement?
    ): List<Node<PsiElement>> {
        if (valueFilterExpression == null) {
            return emptyList()
        }

        val valueMethodCall =
            valueFilterExpression.meaningfulExpression() as? PsiMethodCallExpression
                ?: return emptyList()

        val valueFilterMethod = valueMethodCall.fuzzyResolveMethod() ?: return emptyList()

        if (valueFilterMethod.name == "limit") {
            val actualMethod = valueMethodCall.firstChild?.firstChild as? PsiMethodCallExpression
            return parseFilterRecursively(actualMethod)
        }

        // clean up, we are in the chain where we attach a Sort, Pageable object, so we need to
        // walk upwards in the chain to parse the actual filters
        if (valueFilterMethod.name == "with") {
            val actualMethod = valueMethodCall.firstChild?.firstChild as? PsiMethodCallExpression
            return parseFilterRecursively(actualMethod)
        }

        // clean up, we might be in a query() call
        if (valueFilterMethod.name == "query" || valueFilterMethod.name == "of") {
            return parseFilterRecursively(valueMethodCall.argumentList.expressions.getOrNull(0))
        }

        // 1st scenario: vararg operations
        // for example, andOperator/orOperator...
        if (valueFilterMethod.isVarArgs &&
            valueFilterMethod.name != "in" &&
            valueFilterMethod.name != "nin"
        ) {
            val childrenNodes = valueMethodCall.argumentList.expressions.flatMap {
                parseFilterRecursively(it).reversed()
            }

            val thisQueryNode = listOf(
                Node(
                    valueFilterExpression,
                    listOf(
                        operatorName(valueFilterMethod),
                        HasFilter(childrenNodes)
                    )
                )
            )

            // we finished parsing the vararg operator, check the tail of the query to see if there are more
            // filters.
            //                     v------------------- we want to see if there is something here
            //                                 v------- valueMethodCall is here
            // $nextFieldRef$.$nextValueRef$.$varargOp$("abc")
            val nextQueryExpression = valueMethodCall.firstChild?.firstChild
            if (nextQueryExpression != null && nextQueryExpression is PsiMethodCallExpression) {
                return thisQueryNode + parseFilterRecursively(nextQueryExpression)
            }

            return thisQueryNode
        }

        //                   v----------------------- field filter (it can be a where, an and...)
        //                            v--------------------- optional negation
        //                                    v------------- valueMethodCall
        //                                            v----- the value itself
        // 2nd scenario: $fieldRef$.$not$?.$filter$("abc")
        var negate = false
        var fieldMethodCall =
            valueMethodCall.firstChild.firstChild.meaningfulExpression() as? PsiMethodCallExpression
                ?: return emptyList()

        val fieldMethod = fieldMethodCall.fuzzyResolveMethod() ?: return emptyList()
        if (fieldMethod.name == "not") {
            negate = true
            fieldMethodCall = fieldMethodCall.firstChild?.firstChild?.meaningfulExpression()
                as? PsiMethodCallExpression ?: return emptyList()
        }

        val fieldReference = inferFieldReference(fieldMethodCall)
        val valueReference = inferValueReference(valueMethodCall)

        val operationName = operatorName(valueFilterMethod)

        // we finished parsing the first filter, check the tail of the query to see if there are more
        // filters.
        //                     v------------------- we want to see if there is something here
        //                                 v------- fieldMethodCall is here
        // $nextFieldRef$.$nextValueRef$.$fieldRef$.$filter$("abc")
        val nextQueryExpression = fieldMethodCall.firstChild?.firstChild
        var thisQueryNode = listOf(
            Node(
                valueFilterExpression,
                listOf(
                    operationName,
                    fieldReference,
                    valueReference
                )
            )
        )
        if (negate) {
            thisQueryNode = listOf(
                Node(
                    valueFilterExpression,
                    listOf(
                        Named(Name.NOT),
                        HasFilter(thisQueryNode)
                    )
                )
            )
        }

        if (nextQueryExpression != null && nextQueryExpression is PsiMethodCallExpression) {
            return thisQueryNode + parseFilterRecursively(nextQueryExpression)
        }

        return thisQueryNode
    }

    private fun parseUpdateRecursively(updateExpression: PsiElement?): List<Node<PsiElement>> {
        if (updateExpression == null) {
            return emptyList()
        }

        val updateMethodCall =
            updateExpression.meaningfulExpression() as? PsiMethodCallExpression
                ?: return emptyList()
        val updateMethod = updateMethodCall.fuzzyResolveMethod() ?: return emptyList()

        // 1st scenario, some updates have only 1 argument and are atomic, like
        // currentDate, where we only specify the current field name
        if (updateMethod.name == "currentDate" || updateMethod.name == "currentTimestamp") {
            return parseUpdateRecursively(updateMethodCall.firstChild?.firstChild)
        }

        // 2nd scenario, binary operators like $set
        if (updateMethodCall.argumentList.expressionCount == 2) {
            val arguments = updateMethodCall.argumentList.expressions
            val opName = operatorName(updateMethod)

            val field = psiExpressionToFieldReference(arguments.getOrNull(0))
            val value = psiExpressionToValueReference(arguments.getOrNull(1))

            val thisNode = listOf(Node<PsiElement>(updateMethodCall, listOf(opName, field, value)))
            return thisNode + parseUpdateRecursively(updateMethodCall.firstChild?.firstChild)
        }

        return emptyList()
    }

    private fun inferValueReference(valueMethodCall: PsiMethodCallExpression): HasValueReference<PsiElement> {
        val method = valueMethodCall.fuzzyResolveMethod() ?: return HasValueReference(
            HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
        )

        if (method.name == "in" || method.name == "nin") {
            return varargExpressionListToValueReference(valueMethodCall.argumentList)
        }

        val valuePsi = valueMethodCall.argumentList.expressions.getOrNull(0)
        return psiExpressionToValueReference(valuePsi)
    }

    private fun varargExpressionListToValueReference(argumentList: PsiExpressionList, start: Int = 0): HasValueReference<PsiElement> {
        val valueReference: HasValueReference.ValueReference<PsiElement> =
            if (argumentList.expressionCount == (start + 1)) {
                val secondArg = argumentList.expressions[start].meaningfulExpression() as PsiExpression
                if (secondArg.type?.isJavaIterable() == true) { // case 3
                    argumentList.inferFromSingleVarArgElement(start)
                } else if (secondArg.type?.isArray() == false) { // case 1
                    argumentList.inferFromSingleArrayArgument(start)
                } else { // case 2
                    HasValueReference.Runtime(
                        secondArg,
                        secondArg.type?.toBsonType() ?: BsonArray(BsonAny)
                    )
                }
            } else if (argumentList.expressionCount > (start + 1)) {
                argumentList.inferValueReferenceFromVarArg(start)
            } else {
                HasValueReference.Runtime(argumentList, BsonArray(BsonAny))
            }

        return HasValueReference(valueReference)
    }

    private fun psiExpressionToValueReference(valuePsi: PsiExpression?): HasValueReference<PsiElement> {
        val (_, value) = valuePsi?.tryToResolveAsConstant() ?: (false to null)
        val valueReference = when (value) {
            null -> when (valuePsi?.type) {
                null -> HasValueReference(HasValueReference.Unknown)
                else -> HasValueReference(
                    HasValueReference.Runtime(
                        valuePsi,
                        valuePsi.type?.toBsonType() ?: BsonAny
                    )
                )
            }

            else -> HasValueReference(
                HasValueReference.Constant(valuePsi, value, value.javaClass.toBsonType(value))
            )
        }
        return valueReference as HasValueReference<PsiElement>
    }

    private fun inferFieldReference(fieldMethodCall: PsiElement?): HasFieldReference<PsiElement> {
        if (fieldMethodCall == null || fieldMethodCall !is PsiMethodCallExpression) {
            return HasFieldReference(
                HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>
            )
        }

        val fieldPsi = fieldMethodCall.argumentList.expressions.getOrNull(0)
        return psiExpressionToFieldReference(fieldPsi)
    }

    private fun psiExpressionToFieldReference(fieldPsi: PsiExpression?): HasFieldReference<PsiElement> {
        val fieldReference = when (val field = fieldPsi?.tryToResolveAsConstantString()) {
            null -> HasFieldReference(
                HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>
            )
            else -> HasFieldReference(HasFieldReference.FromSchema<PsiElement>(fieldPsi, field))
        }

        return fieldReference
    }

    private fun operatorName(currentCriteriaMethod: PsiMethod): Named {
        if (currentCriteriaMethod.name == "update") {
            return Named(Name.SET)
        }

        val name = currentCriteriaMethod.name.replace("Operator", "")
        val named = Named(name.toName())
        return named
    }

    private fun parseLimitCall(element: PsiElement?): HasLimit? {
        val methodCallExpr = element?.meaningfulExpression() as? PsiMethodCallExpression
            ?: return null
        val limitMethodCall = methodCallExpr.gatherChainedCalls().find {
            val method = it.fuzzyResolveMethod()
            method?.name == "limit" && method.containingClass?.qualifiedName == QUERY_CLASS_FQN
        } ?: return null

        val limitArg = limitMethodCall.argumentList.expressions.getOrNull(0) ?: return null
        val (wasResolved, limitValue) = limitArg.tryToResolveAsConstant()
        val limitInt = if (wasResolved) {
            limitValue as? Int
        } else {
            null
        } ?: return null

        return HasLimit(limitInt)
    }

    /**
     * List of methods from here:
     * https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/core/MongoOperations.html
     */
    private fun inferCommandFromMethod(mongoOpMethod: PsiMethod?): IsCommand {
        return IsCommand(
            when (mongoOpMethod?.name) {
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
                "all", "matching" -> IsCommand.CommandType.FIND_MANY
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

fun PsiMethodCallExpression.isSuitableForFieldAutoCompleteInAggregation(
    parsers: List<StageParser>
): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return parsers.any { it.isSuitableForFieldAutoComplete(this, method) }
}

private fun PsiMethodCallExpression.findSpringMongoDbExpression(): PsiMethodCallExpression? {
    val method = fuzzyResolveMethod() ?: return null
    if (INTERFACES_WITH_QUERY_METHODS.any {
            method.containingClass?.qualifiedName?.contains(it) == true
        }
    ) {
        val parentMethodCall = findParentOfType<PsiMethodCallExpression>()
        return parentMethodCall?.findSpringMongoDbExpression() ?: this
    } else {
        val parentMethodCall = findParentOfType<PsiMethodCallExpression>() ?: return null
        return parentMethodCall.findSpringMongoDbExpression()
    }
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
    "ReactiveMongoTemplate",
    "ReactiveMongoOperations",
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
    "TerminatingFind",
)

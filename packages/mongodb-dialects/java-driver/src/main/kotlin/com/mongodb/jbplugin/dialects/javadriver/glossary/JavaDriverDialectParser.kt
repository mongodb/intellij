package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement): Boolean {
        if (source !is PsiMethodCallExpression) {
// if it's not a method call, like .find(), it's not a query
            return false
        }
        val sourceMethod = source.resolveMethod() ?: return false

        if ( // if the method is of MongoCollection, then we are in a query
            sourceMethod.containingClass?.isMongoDbCollectionClass(source.project) == true
        ) {
            return true
        }

        if ( // if it's any driver class, check inner calls
            sourceMethod.containingClass?.isMongoDbClass(source.project) == true
        ) {
            val allChildrenCandidates = PsiTreeUtil.findChildrenOfType(source, PsiMethodCallExpression::class.java)
            return allChildrenCandidates.any { isCandidateForQuery(it) }
        }

        return false
    }

    override fun attachment(source: PsiElement): PsiElement = source.findMongoDbCollectionReference()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val namespace = NamespaceExtractor.extractNamespace(source)
        val currentCall = source as PsiMethodCallExpression? ?: return Node(source, emptyList())

        val hasChildren =
            if (currentCall.argumentList.expressionCount > 0) {
// we have at least 1 argument in the current method call
                val argumentAsFilters = resolveToFiltersCall(currentCall.argumentList.expressions[0])
                argumentAsFilters?.let {
                    val parsedQuery = parseFilterExpression(argumentAsFilters) // assume it's a Filters call
                    parsedQuery?.let {
                        HasChildren(
                            listOf(
                                parseFilterExpression(
                                    argumentAsFilters,
                                )!!,
                            ),
                        )
                    } ?: HasChildren(emptyList())
                } ?: HasChildren(emptyList())
            } else {
                HasChildren(emptyList())
            }

        return Node(
            source,
            listOf(
                namespace?.let {
                    HasCollectionReference(HasCollectionReference.Known(namespace))
                } ?: HasCollectionReference(HasCollectionReference.Unknown),
                hasChildren,
            ),
        )
    }

    private fun parseFilterExpression(filter: PsiMethodCallExpression): Node<PsiElement>? {
        val method = filter.resolveMethod() ?: return null
        if (method.isVarArgs) {
// Filters.and, Filters.or... are varargs
            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasChildren(
                        filter.argumentList.expressions
                            .filterIsInstance<PsiMethodCallExpression>()
                            .mapNotNull { parseFilterExpression(it) },
                    ),
                ),
            )
        } else if (method.parameters.size == 2) {
// If it has two parameters, it's field/value.
            val fieldNameAsString = filter.argumentList.expressions[0].tryToResolveAsConstantString()
            val fieldReference =
                fieldNameAsString?.let {
                    HasFieldReference.Known(filter.argumentList.expressions[0], fieldNameAsString)
                } ?: HasFieldReference.Unknown

            val constantValue = filter.argumentList.expressions[1].tryToResolveAsConstant()
            val typeOfConstantValue = constantValue?.javaClass?.toBsonType()

            val valueReference =
                if (constantValue != null && typeOfConstantValue != null) {
                    HasValueReference.Constant(constantValue, typeOfConstantValue)
                } else {
                    val psiTypeOfValue =
                        filter.argumentList.expressions[1]
                            .type
                            ?.toBsonType()
                    psiTypeOfValue?.let {
                        HasValueReference.Runtime(psiTypeOfValue)
                    } ?: HasValueReference.Unknown
                }

            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasFieldReference(
                        fieldReference,
                    ),
                    HasValueReference(
                        valueReference,
                    ),
                ),
            )
        }

        return null
    }

    private fun resolveToFiltersCall(element: PsiElement): PsiMethodCallExpression? {
        when (element) {
            is PsiMethodCallExpression -> return element
            is PsiVariable -> {
                element.initializer ?: return null
                return resolveToFiltersCall(element.initializer!!)
            }
            is PsiReferenceExpression -> {
                val referredValue = element.resolve() ?: return null
                return resolveToFiltersCall(referredValue)
            }
            else -> return null
        }
    }
}
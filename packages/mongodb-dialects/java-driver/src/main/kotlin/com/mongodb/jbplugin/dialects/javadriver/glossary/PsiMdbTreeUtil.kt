/**
 * Defines a set of extension methods to extract metadata from a Psi tree.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiLiteralValue
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonEnum
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.BsonUUID
import com.mongodb.jbplugin.mql.components.IsCommand

/**
 * Helper extension function to get the containing class of any element.
 *
 * @return
 */
fun PsiElement.findContainingClass(): PsiClass =
    parentOfType<PsiClass>(withSelf = true)
        ?: childrenOfType<PsiClass>().first()

/**
 * Helper function to check if a type is a MongoDB Collection
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbCollectionClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbCollectionClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoCollection",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbCollectionClass
}

/**
 * Helper function to check if a type is a MongoDB Collection
 *
 * @param project
 */
fun PsiType.isMongoDbCollectionClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbCollectionClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Database
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbDatabaseClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbDatabaseClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoDatabase",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbDatabaseClass
}

/**
 * Helper function to check if a type is a MongoDB Database
 *
 * @param project
 * @return
 */
fun PsiType.isMongoDbDatabaseClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbDatabaseClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Client
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbClientClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbClientClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoClient",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbClientClass
}

/**
 * Helper function to check if a type is a MongoDB Class
 *
 * @param project
 * @return
 */
fun PsiType?.isMongoDbClass(project: Project): Boolean =
    PsiTypesUtil.getPsiClass(this)?.run {
        isMongoDbCollectionClass(project) ||
            isMongoDbDatabaseClass(project) ||
            isMongoDbClientClass(project)
    } == true

/**
 * Collects all elements of type T upwards until a type S is found.
 *
 * @param type
 * @param stopWord
 */
fun <T : PsiElement, S : PsiElement> PsiElement.collectTypeUntil(
    type: Class<T>,
    stopWord: Class<S>,
): List<T> {
    if (stopWord.isInstance(this)) {
        return emptyList()
    }

    if (type.isInstance(this)) {
        return listOf(this as T) + (this.parent?.collectTypeUntil(type, stopWord) ?: emptyList())
    }

    return this.parent?.collectTypeUntil(type, stopWord) ?: emptyList()
}

/**
 * Returns the reference to any MongoDB driver call.
 *
 * @param project
 */
fun PsiMethodCallExpression.findMongoDbClassReference(project: Project): PsiExpression? {
    if (methodExpression.type?.isMongoDbClass(project) == true) {
        return methodExpression
    } else if (methodExpression.qualifierExpression is PsiMethodCallExpression) {
        return (methodExpression.qualifierExpression as PsiMethodCallExpression).findMongoDbClassReference(
            project
        )
    } else if (methodExpression.qualifierExpression?.reference?.resolve() is PsiField) {
        return methodExpression.qualifierExpression
    } else {
        val method = resolveMethod() ?: return null
        return method.body
            ?.collectTypeUntil(PsiMethodCallExpression::class.java, PsiMethod::class.java)
            ?.firstNotNullOfOrNull { it.findMongoDbClassReference(it.project) }
    }
}

/**
 * An identified command will not always map to the corresponding method call on MongoCollection
 * class; see the mapping of FIND_ONE command for example and when we come across one such command,
 * we might need to do a little more and find the appropriate MongoCollection method call.
 *
 * Returns the PsiMethodCallExpression that correctly maps to the command provided.
 * @param identifiedCommand
 */
fun PsiMethodCallExpression.findMongoDbCollectionMethodCallForCommand(
    identifiedCommand: IsCommand,
): PsiMethodCallExpression {
    val currentCall = this
    return if (identifiedCommand.type == IsCommand.CommandType.FIND_ONE) {
        val allCallExpressions = currentCall.findAllChildrenOfType(
            PsiMethodCallExpression::class.java
        )
        allCallExpressions.getOrNull(
            allCallExpressions.lastIndex - 1
        )!!
    } else {
        currentCall
    }
}

/**
 * Returns the reference to a MongoDB driver collection.
 */
fun PsiElement.findMongoDbCollectionReference(): PsiExpression? {
    when (this) {
        is PsiMethodCallExpression -> {
            return if (methodExpression.type?.isMongoDbCollectionClass(project) == true) {
                methodExpression
            } else if (methodExpression.qualifierExpression is PsiMethodCallExpression) {
                (methodExpression.qualifierExpression as PsiMethodCallExpression).findMongoDbCollectionReference()
            } else if (methodExpression.qualifierExpression?.reference?.resolve() is PsiField) {
                methodExpression.qualifierExpression
            } else {
                methodExpression.children.firstNotNullOfOrNull {
                    it.findMongoDbCollectionReference()
                }
            }
        }
        is PsiExpression -> {
            if (this.type?.isMongoDbCollectionClass(project) == true) {
                return this
            }

            return null
        }
        else -> {
            return children.firstNotNullOfOrNull { it.findMongoDbCollectionReference() }
        }
    }
}

/**
 * Resolves to a pair of the resolved value in the expression and whether it was possible to
 * resolve the value or not.
 *
 * @return Pair<Boolean, Any?> A pair where the first component represents whether
 * the value was resolved during compile time or not and the second component
 * represents the resolved value itself
 */
fun PsiElement.tryToResolveAsConstant(): Pair<Boolean, Any?> {
    val meaningfulThis = meaningfulExpression()

    if (meaningfulThis is PsiEnumConstant) {
        return true to meaningfulThis
    } else if (meaningfulThis is PsiReferenceExpression) {
        val varRef = meaningfulThis.resolve()
        if (varRef == null) {
            return false to null
        }
        return varRef.tryToResolveAsConstant()
    } else if (meaningfulThis is PsiLocalVariable && meaningfulThis.initializer != null) {
        return meaningfulThis.initializer!!.tryToResolveAsConstant()
    } else if (meaningfulThis is PsiLiteralValue) {
        val facade = JavaPsiFacade.getInstance(meaningfulThis.project)
        val resolvedValue = facade.constantEvaluationHelper.computeConstantExpression(
            meaningfulThis
        )
        return true to resolvedValue
    } else if (meaningfulThis is PsiLiteralExpression) {
        val facade = JavaPsiFacade.getInstance(meaningfulThis.project)
        val resolvedValue = facade.constantEvaluationHelper.computeConstantExpression(
            meaningfulThis
        )
        return true to resolvedValue
    } else if (meaningfulThis is PsiField &&
        meaningfulThis.initializer != null &&
        meaningfulThis.hasModifier(JvmModifier.FINAL)
    ) {
        return meaningfulThis.initializer!!.tryToResolveAsConstant()
    } else if (meaningfulThis is PsiMethodCallExpression) {
        val methodCall = meaningfulThis.fuzzyResolveMethod() ?: return false to null
        return PsiTreeUtil.findChildrenOfType(
            methodCall.body,
            PsiReturnStatement::class.java
        )
            .mapNotNull { it.returnValue }
            .map { it.tryToResolveAsConstant() }
            .firstOrNull { it.first } ?: (false to null)
    }

    return false to null
}

/**
 * Resolves to the value of the expression to a string
 * if it's known at compile time.
 *
 * @return
 */
fun PsiElement.tryToResolveAsConstantString(): String? =
    tryToResolveAsConstant().takeIf { it.first }?.second as? String

/**
 * Maps a PsiType to its BSON counterpart.
 * PsiClassReferenceType
 */
fun PsiType.toBsonType(): BsonType {
    val javaClass = when (this) {
        is PsiClassReferenceType, is PsiImmediateClassType -> resolve() ?: return BsonAny
        else -> null
    }

    if (javaClass?.isEnum == true) {
        val enumConstants = javaClass.findAllChildrenOfType(PsiEnumConstant::class.java)
        return BsonEnum(enumConstants.map { it.name }.toSet(), javaClass.name)
    }

    return this.canonicalText.toBsonType()
}

/**
 * Maps a Java FQN to a BsonType.
 */
fun String.toBsonType(): BsonType {
    if (this == ("org.bson.types.ObjectId")) {
        return BsonAnyOf(BsonObjectId, BsonNull)
    } else if (this == ("boolean") || this == ("java.lang.Boolean")) {
        return BsonBoolean
    } else if (this == ("short") || this == ("java.lang.Short")) {
        return BsonInt32
    } else if (this == ("int") || this == ("java.lang.Integer")) {
        return BsonInt32
    } else if (this == ("long") || this == ("java.lang.Long")) {
        return BsonInt64
    } else if (this == ("float") || this == ("java.lang.Float")) {
        return BsonDouble
    } else if (this == ("double") || this == ("java.lang.Double")) {
        return BsonDouble
    } else if (this == ("java.lang.CharSequence") ||
        this == ("java.lang.String") ||
        this == "String"
    ) {
        return BsonAnyOf(BsonString, BsonNull)
    } else if (this == ("java.util.Date") ||
        this == ("java.time.Instant") ||
        this == ("java.time.LocalDate") ||
        this == ("java.time.LocalDateTime")
    ) {
        return BsonAnyOf(BsonDate, BsonNull)
    } else if (this == ("java.math.BigInteger")) {
        return BsonAnyOf(BsonInt64, BsonNull)
    } else if (this == ("java.math.BigDecimal")) {
        return BsonAnyOf(BsonDecimal128, BsonNull)
    } else if (this.endsWith("[]")) {
        val baseType = this.substring(0, this.length - 2)
        return BsonArray(baseType.toBsonType())
    } else if (this.contains("List") || this.contains("Set")) {
        if (!this.contains("<")) { // not passing the generic types, so assume an array of BsonAny
            return BsonArray(BsonAny)
        }

        val baseType = this.substringAfter("<").substringBeforeLast(">")
        return BsonArray(baseType.toBsonType())
    } else if (this == ("java.util.UUID")) {
        return BsonUUID
    }

    return BsonAny
}

/**
 * Returns all children of type in a list. Order is not guaranteed between calls.
 * It also takes into consideration in method calls, the parameters of the method call.
 *
 * @param type
 */
fun <T> PsiElement.findAllChildrenOfType(type: Class<T>): List<T> {
    val allChildren = this.children.flatMap { it.findAllChildrenOfType(type) }.toMutableList()

    if (this is PsiMethodCallExpression) {
        allChildren += this.argumentList.expressions.flatMap { it.findAllChildrenOfType(type) }
    }

    if (type.isInstance(this)) {
        allChildren += listOf(this as T)
    }

    return allChildren
}

/**
 * Resolves to the first meaningful expression in a tree. Not all expressions have an important
 * meaning for us (like for example, parenthesized expressions) so we drop them and get any
 * inner expression that is relevant for us.
 */
fun PsiElement.meaningfulExpression(): PsiElement {
    return when (this) {
        // the children are: '(', YOUR_EXPR, ')'
        // so we need the expression inside (index 1)
        is PsiParenthesizedExpression -> if (children.size == 3) {
            children[1].meaningfulExpression()
        } else {
            this
        }
        else -> this
    }
}

/**
 * Resolves the method fuzzily. In case that a single implementation has multiple overloads, the
 * default implementation from IntelliJ returns null, we will just get the first of them.
 */
fun PsiMethodCallExpression.fuzzyResolveMethod(): PsiMethod? {
    val allResolutions = methodExpression.multiResolve(false)

    if (allResolutions.isEmpty()) {
        return null
    }

    return allResolutions.first().element as? PsiMethod
}

/**
 * Returns the first parent matching a condition.
 */
fun PsiElement?.findTopParentBy(filter: (PsiElement) -> Boolean): PsiElement? {
    return if (this != null && filter(this)) {
        this.parent?.findTopParentBy(filter) ?: this
    } else {
        this?.parent?.findTopParentBy(filter)
    }
}

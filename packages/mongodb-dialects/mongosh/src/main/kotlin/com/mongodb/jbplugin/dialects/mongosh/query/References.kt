package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.backend.ContextValue
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.components.HasFieldReference.Inferred
import com.mongodb.jbplugin.mql.components.HasFieldReference.Unknown
import com.mongodb.jbplugin.mql.components.HasValueReference

fun <S> MongoshBackend.resolveValueReference(
    valueRef: HasValueReference<S>,
    fieldRef: HasFieldReference<S>?
): ContextValue {
    return when (val ref = valueRef.reference) {
        is HasValueReference.Computed -> {
            val node = ref.type.expression
            val fieldRef =
                node.component<HasFieldReference<S>>()
                    ?: return registerVariable("queryField", ref.type.baseType, null)

            resolveFieldReference(fieldRef)
        }
        is HasValueReference.Constant -> registerConstant(ref.value)
        is HasValueReference.Inferred -> registerConstant(ref.value)
        is HasValueReference.Runtime -> registerVariable(
            (fieldRef?.reference as? FromSchema)?.fieldName ?: "value",
            ref.type,
            null
        )

        else -> registerVariable(
            "queryField",
            BsonAny,
            null
        )
    }
}

fun <S> MongoshBackend.resolveFieldReference(fieldRef: HasFieldReference<S>) =
    when (val ref = fieldRef.reference) {
        is FromSchema -> registerConstant(ref.displayName)
        is Inferred<S> -> registerConstant(ref.displayName)
        is HasFieldReference.Computed<S> -> registerConstant(ref.displayName)
        is Unknown -> registerVariable("field", BsonAny, null)
    }

fun <S> MongoshBackend.emitCollectionReference(collRef: HasCollectionReference<S>?): MongoshBackend {
    when (val ref = collRef?.reference) {
        is HasCollectionReference.OnlyCollection -> {
            emitDatabaseAccess(registerVariable("database", BsonString, null))
            emitCollectionAccess(registerConstant(ref.collection))
        }

        is HasCollectionReference.Known -> {
            emitDatabaseAccess(registerConstant(ref.namespace.database))
            emitCollectionAccess(registerConstant(ref.namespace.collection))
        }

        else -> {
            emitDatabaseAccess(registerVariable("database", BsonString, null))
            emitCollectionAccess(registerVariable("collection", BsonString, null))
        }
    }

    return this
}

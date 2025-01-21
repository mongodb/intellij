package com.mongodb.jbplugin.dialects.mongosh.query

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
) = when (val ref = valueRef.reference) {
    is HasValueReference.Constant -> registerConstant(ref.value)
    is HasValueReference.Inferred -> registerConstant(ref.value)
    is HasValueReference.Runtime -> registerVariable(
        (fieldRef?.reference as? FromSchema)?.fieldName ?: "value",
        ref.type
    )

    else -> registerVariable(
        "queryField",
        BsonAny
    )
}

fun <S> MongoshBackend.resolveFieldReference(fieldRef: HasFieldReference<S>) =
    when (val ref = fieldRef.reference) {
        is FromSchema -> registerConstant(ref.fieldName)
        is Inferred<S> -> registerConstant(ref.fieldName)
        is HasFieldReference.Computed<S> -> registerConstant(ref.fieldName)
        is Unknown -> registerVariable("field", BsonAny)
    }

fun <S> MongoshBackend.emitCollectionReference(collRef: HasCollectionReference<S>?): MongoshBackend {
    when (val ref = collRef?.reference) {
        is HasCollectionReference.OnlyCollection -> {
            emitDatabaseAccess(registerVariable("database", BsonString))
            emitCollectionAccess(registerConstant(ref.collection))
        }

        is HasCollectionReference.Known -> {
            emitDatabaseAccess(registerConstant(ref.namespace.database))
            emitCollectionAccess(registerConstant(ref.namespace.collection))
        }

        else -> {
            emitDatabaseAccess(registerVariable("database", BsonString))
            emitCollectionAccess(registerVariable("collection", BsonString))
        }
    }

    return this
}

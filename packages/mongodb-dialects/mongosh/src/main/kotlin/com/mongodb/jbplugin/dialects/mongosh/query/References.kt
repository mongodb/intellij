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

/**
 * Prefixes a String with a '$' character if not already prefixed in order to correctly
 * represent a MongoDB schema field reference when the field is used as a value
 */
fun String.toMongoDBSchemaField(fieldUsedAsValue: Boolean): String {
    return if (fieldUsedAsValue) {
        if (startsWith('$')) {
            this
        } else {
            "${'$'}$this"
        }
    } else {
        this.trim('$')
    }
}

fun <S> MongoshBackend.resolveValueReference(
    valueRef: HasValueReference<S>,
    fieldRef: HasFieldReference<S>?
): ContextValue {
    return when (val ref = valueRef.reference) {
        is HasValueReference.Computed -> {
            val node = ref.type.expression
            // There could be multiple field references in a Computed ValueReference
            val nestedFieldRefs = node.components<HasFieldReference<S>>()
            if (nestedFieldRefs.isEmpty()) {
                // We treat the empty case of nested fields refs as not providing field
                // and hence we register null as the value for that
                registerConstant(null)
            } else if (nestedFieldRefs.size == 1) {
                resolveFieldReference(
                    fieldRef = nestedFieldRefs.first(),
                    fieldUsedAsValue = true
                )
            } else {
                // For a list of field references we map them to a Map of fieldName to displayName
                val mapOfFields = nestedFieldRefs.map { nestedFieldRef ->
                    when (val nestedRef = nestedFieldRef.reference) {
                        is FromSchema ->
                            nestedRef.fieldName to nestedRef.displayName.toMongoDBSchemaField(
                                fieldUsedAsValue = true
                            )
                        is Inferred<S> ->
                            nestedRef.fieldName to nestedRef.displayName.toMongoDBSchemaField(
                                fieldUsedAsValue = true
                            )
                        is HasFieldReference.Computed<S> ->
                            nestedRef.fieldName to nestedRef.displayName.toMongoDBSchemaField(
                                fieldUsedAsValue = true
                            )
                        // If any of the nestedFieldRef is Unknown then we fallback to simply
                        // registering a variable value
                        is Unknown -> return registerVariable(
                            (fieldRef?.reference as? FromSchema)?.fieldName ?: "${'$'}value",
                            ref.type,
                            null
                        )
                    }
                }.toMap()
                registerConstant(mapOfFields)
            }
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

fun <S> MongoshBackend.resolveFieldReference(
    fieldRef: HasFieldReference<S>,
    fieldUsedAsValue: Boolean,
): ContextValue {
    return when (val ref = fieldRef.reference) {
        is FromSchema -> registerConstant(ref.displayName.toMongoDBSchemaField(fieldUsedAsValue))
        is Inferred<S> -> registerConstant(ref.displayName.toMongoDBSchemaField(fieldUsedAsValue))
        is HasFieldReference.Computed<S> ->
            registerConstant(ref.displayName.toMongoDBSchemaField(fieldUsedAsValue))
        is Unknown ->
            registerVariable("field".toMongoDBSchemaField(fieldUsedAsValue), BsonAny, null)
    }
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

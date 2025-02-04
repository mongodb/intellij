package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectFormatter
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.BsonUUID
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.parser.components.allNodesWithKnownRuntimeFields
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.toNonNullableType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.jetbrains.letsPlot.commons.intern.filterNotNullValues
import java.awt.event.ActionEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel

class RunQueryModal(
    private val query: Node<PsiElement>,
    private val dataSource: LocalDataSource,
    private val coroutineScope: CoroutineScope
) : DialogWrapper(query.source.project, false) {
    private var namespaceSelector: NamespaceSelector? = null
    private val fieldsForContext: MutableList<Triple<String, BsonType, JComponent>> =
        mutableListOf()

    public override fun createCenterPanel(): JComponent? {
        fieldsForContext.clear()

        val builder = FormBuilder.createFormBuilder()
        val allEmptyFields = allRuntimeFieldNodes()

        builder.addComponent(
            JBLabel(
                CodeActionsMessages.message("code.action.run.query.modal.title"),
                UIUtil.ComponentStyle.LARGE
            )
        )

        builder.addComponent(
            JBLabel(
                CodeActionsMessages.message("code.action.run.query.modal.subtitle"),
                UIUtil.ComponentStyle.SMALL,
                UIUtil.FontColor.BRIGHTER
            )
        )

        builder.addComponent(JPanel(), 12)

        val collectionReference = query.component<HasCollectionReference<PsiElement>>()?.reference
        when (collectionReference) {
            is HasCollectionReference.Known -> {}
            else -> {
                namespaceSelector =
                    NamespaceSelector(query.source.project, dataSource, coroutineScope)

                builder.addLabeledComponent(
                    CodeActionsMessages.message("code.action.run.query.modal.database"),
                    namespaceSelector!!.databaseComboBox
                )
                builder.addLabeledComponent(
                    CodeActionsMessages.message("code.action.run.query.modal.collection"),
                    namespaceSelector!!.collectionComboBox
                )

                builder.addSeparator()
            }
        }

        for (node in allEmptyFields) {
            val fieldName =
                node.component<HasFieldReference<PsiElement>>()?.reference as? HasFieldReference.FromSchema
                    ?: continue
            val fieldValue =
                node.component<HasValueReference<PsiElement>>()?.reference as? HasValueReference.Runtime
                    ?: continue

            val type = fieldValue.type.toNonNullableType()
            val typeWithJavaRepresentation = JavaDriverDialectFormatter.formatType(type)
            val label = JBLabel("${fieldName.fieldName} ($typeWithJavaRepresentation):")
            val (input, toolTip) = toInput(type) ?: continue

            fieldsForContext += Triple(fieldName.fieldName, type, input)

            input.name = fieldName.fieldName
            builder.addLabeledComponent(label, input, 8)
            if (toolTip != null) {
                builder.addTooltip(toolTip)
            }
        }

        val form = builder.panel
        form.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        return form
    }

    override fun createActions(): Array<out Action?> {
        return arrayOf(
            object : DialogWrapperAction(
                CodeActionsMessages.message("code.action.run.query.modal.action.cancel")
            ) {
                override fun doAction(e: ActionEvent?) {
                    close(CANCEL_EXIT_CODE)
                }
            },
            object : DialogWrapperAction(
                CodeActionsMessages.message("code.action.run.query.modal.action.generate")
            ) {
                init {
                    putValue(DEFAULT_ACTION, true)
                }

                override fun doAction(e: ActionEvent?) {
                    close(OK_EXIT_CODE)
                }
            }
        )
    }

    /**
     * Opens the modal so the user can fill default values for variables in the query.
     *
     * @return null when the user cancels the modal.
     * @return QueryContext.empty() when all fields are known at compile time.
     * @return QueryContext when the user fills the modal and confirms.
     */
    fun askForQueryContext(): QueryContext? {
        val allNodes = allRuntimeFieldNodes()
        if (allNodes.isEmpty()) {
            return QueryContext.empty(prettyPrint = true)
        }

        init()
        (peer.window as? JDialog)?.isUndecorated = true

        return if (showAndGet()) {
            buildQueryContextFromModal()
        } else {
            null
        }
    }

    private fun allRuntimeFieldNodes(): List<Node<PsiElement>> =
        runBlocking {
            allNodesWithKnownRuntimeFields<PsiElement>().parse(query).orElse {
                emptyList()
            }
        }

    private fun toInput(type: BsonType): Pair<JComponent, String?>? {
        return when (type.toNonNullableType()) {
            BsonInt32, BsonInt64, BsonDouble, BsonDecimal128, BsonString -> JBTextField() to null
            BsonBoolean -> JBCheckBox() to null
            BsonUUID -> JBTextField(sampleUuid()) to null
            BsonObjectId ->
                JBTextField(sampleObjectId()) to
                    CodeActionsMessages.message("code.action.run.query.modal.insight.objectid")

            BsonDate ->
                JBTextField(sampleDateTime()) to
                    CodeActionsMessages.message("code.action.run.query.modal.insight.date")

            else ->
                JBLabel(
                    CodeActionsMessages.message(
                        "code.action.run.query.modal.warning.can.not.specify"
                    )
                ) to
                    null
        }
    }

    internal fun buildQueryContextFromModal(): QueryContext {
        val localVariables = fieldsForContext.groupBy { it.first }
            .mapValues { it.value.first().let { it.second to it.third } }
            .mapValues { (_, value) ->
                when (value.second) {
                    is JBTextField -> mapToLocalVariable(
                        value.first,
                        (value.second as JBTextField).text
                    )
                    is JBCheckBox -> QueryContext.LocalVariable(
                        value.first,
                        (value.second as JBCheckBox).isSelected
                    )
                    else -> null
                }
            }.filterNotNullValues()
            .toMutableMap()

        namespaceSelector?.let {
            localVariables.put(
                "database",
                QueryContext.LocalVariable(BsonString, it.selectedDatabase)
            )
            localVariables.put(
                "collection",
                QueryContext.LocalVariable(BsonString, it.selectedCollection)
            )
        }

        return QueryContext(localVariables, QueryContext.ExplainPlanType.NONE, prettyPrint = true)
    }

    private fun mapToLocalVariable(type: BsonType, value: String): QueryContext.LocalVariable {
        return when (type) {
            BsonInt32 -> QueryContext.LocalVariable(BsonInt32, parseOrAsIs(value, String::toInt))
            BsonInt64 -> QueryContext.LocalVariable(BsonInt64, parseOrAsIs(value, String::toLong))
            BsonDouble -> QueryContext.LocalVariable(
                BsonDouble,
                parseOrAsIs(value, String::toDouble)
            )
            BsonDecimal128 -> QueryContext.LocalVariable(
                BsonDecimal128,
                parseOrAsIs(value, String::toBigDecimal)
            )
            BsonString -> QueryContext.LocalVariable(BsonString, value)
            BsonUUID -> QueryContext.LocalVariable(BsonUUID, parseOrAsIs(value, UUID::fromString))
            BsonDate -> QueryContext.LocalVariable(
                BsonDate,
                parseOrAsIs(value) {
                    DATE_FORMATTER.parse(it)
                }
            )
            BsonObjectId -> QueryContext.LocalVariable(BsonObjectId, parseOrAsIs(value, ::ObjectId))
            else -> QueryContext.LocalVariable(type, "")
        }
    }

    private fun <S : Any> parseOrAsIs(value: String, parser: (String) -> S): Any {
        return runCatching { parser.invoke(value) }
            .getOrElse { QueryContext.AsIs(value) }
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME

        internal fun sampleDateTime() = DATE_FORMATTER.format(LocalDateTime.now())
        internal fun sampleObjectId() = ObjectId().toHexString()
        internal fun sampleUuid() = UUID.randomUUID().toString()
    }
}

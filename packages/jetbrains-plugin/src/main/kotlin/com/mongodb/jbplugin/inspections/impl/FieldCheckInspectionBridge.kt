/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.linting.FieldCheckWarning
import com.mongodb.jbplugin.linting.FieldCheckingLinter
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference

@Suppress("MISSING_KDOC_TOP_LEVEL")
class FieldCheckInspectionBridge :
    AbstractMongoDbInspectionBridge(
        FieldCheckLinterInspection,
    )

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object FieldCheckLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return registerNoConnectionProblem(problems, query.source)
        }

        if (query.component<HasCollectionReference>()?.reference is HasCollectionReference.OnlyCollection) {
            return registerNoDatabaseSelectedProblem(problems, query.source)
        }

        val readModelProvider = query.source.project.getService(DataGripBasedReadModelProvider::class.java)

        val result =
            FieldCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )

        result.warnings.forEach {
            when (it) {
                is FieldCheckWarning.FieldDoesNotExist -> registerFieldDoesNotExistProblem(problems, it)
                is FieldCheckWarning.FieldValueTypeMismatch -> registerFieldValueTypeMismatch(problems, it, formatter)
            }
        }
    }

    private fun registerNoConnectionProblem(problems: ProblemsHolder, source: PsiElement) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.connection",
        )
            problems.registerProblem(
                source,
                problemDescription,
                ProblemHighlightType.WARNING,
                OpenConnectionChooserQuickFix(
                    InspectionsAndInlaysMessages.message(
                        "inspection.field.checking.quickfix.choose.new.connection"
                    ),
                ),
            )
    }

    private fun registerNoDatabaseSelectedProblem(problems: ProblemsHolder, source: PsiElement) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.database",
        )
        if (!problems.isProblemAlreadyRegistered(problemDescription, source)) {
            problems.registerProblem(
                source,
                problemDescription,
                ProblemHighlightType.WARNING,
                OpenConnectionChooserQuickFix(
                    InspectionsAndInlaysMessages.message(
                        "inspection.field.checking.quickfix.choose.new.connection"
                    ),
                ),
            )
        }
    }

    private fun registerFieldDoesNotExistProblem(
        problems: ProblemsHolder,
        warningInfo: FieldCheckWarning.FieldDoesNotExist<PsiElement>,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message",
            warningInfo.field,
            warningInfo.namespace,
        )
            problems.registerProblem(
                warningInfo.source,
                problemDescription,
                ProblemHighlightType.WARNING,
                OpenConnectionChooserQuickFix(
                    InspectionsAndInlaysMessages.message("inspection.field.checking.quickfix.choose.new.connection"),
                ),
            )
    }

    private fun registerFieldValueTypeMismatch(
        problems: ProblemsHolder,
        warningInfo: FieldCheckWarning.FieldValueTypeMismatch<PsiElement>,
        formatter: DialectFormatter,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.value.type.mismatch",
            formatter.formatType(warningInfo.valueType),
            formatter.formatType(warningInfo.fieldType),
            warningInfo.field,
        )
            problems.registerProblem(
                warningInfo.valueSource,
                problemDescription,
                ProblemHighlightType.WARNING,
                OpenConnectionChooserQuickFix(
                    InspectionsAndInlaysMessages.message("inspection.field.checking.quickfix.choose.new.connection"),
                ),
            )
    }

    /**
     * This quickfix opens a modal with the connection chooser.
     *
     * @param message
     */
    private class OpenConnectionChooserQuickFix(
        private val message: String,
    ) : LocalQuickFix {
        override fun getFamilyName(): String = message

        override fun applyFix(
            project: Project,
            descriptor: ProblemDescriptor,
        ) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditorWithRemotes[0] ?: return
            MdbJavaEditorToolbar.showModalForSelection(editor, project.coroutineScope)
        }
    }
}

package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * This quickfix opens a modal with the connection chooser.
 *
 * @param coroutineScope
 */
class OpenConnectionChooserQuickFix(
    private val coroutineScope: CoroutineScope
) : LocalQuickFix {
    override fun getFamilyName(): String = InspectionsAndInlaysMessages.message(
        "inspection.field.checking.quickfix.choose.new.connection"
    )

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {
        coroutineScope.launch(Dispatchers.EDT) {
            MdbJavaEditorToolbar.showModalForSelection(project, coroutineScope)
        }
    }
}

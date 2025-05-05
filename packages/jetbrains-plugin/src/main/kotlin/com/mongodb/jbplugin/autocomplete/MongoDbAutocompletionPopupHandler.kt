/**
 * This file sets up the autocompletion handler to programmatically trigger the completion popup when we start to write
 * a string in a MongoDB Query.
 */

package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaToken
import com.intellij.util.ThreeState
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.meta.invokeInEdt
import com.mongodb.jbplugin.meta.withinReadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * This class listens to all keystrokes in the editor. It contains a processing loop that consumes events from the
 * MutableSharedFlow and triggers the autocompletion popup if the keystroke was an opening quote in a MongoDB query.
 *
 * All complex logic of parsing the query needs to be done in the processing loop as it runs in its custom coroutine,
 * avoiding blocking the UI. <b>Do not run anything on beforeCharTyped</b> and avoid using the EDT as much as possible.
 *
 * @param coroutineScope
 */
class MongoDbAutocompletionPopupHandler(
    private val coroutineScope: CoroutineScope
) : TypedHandlerDelegate() {
    private val events =
        MutableSharedFlow<AutocompletionEvent>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    init {
        /*
         * It's complaining on our returns, but we need them for early returning and avoid executing code we don't need
         * to
         */
        @Suppress("CUSTOM_LABEL")
        coroutineScope.launch(Dispatchers.IO) {
            events.collectLatest { event ->
                if (event.file.dataSource == null ||
                    event.file.dataSource?.isConnected() == false
                ) {
                    return@collectLatest
                }

                withinReadAction {
                    val elementAtCaret =
                        event.file.findElementAt(
                            event.editor.caretModel.offset
                        )?.originalElement
                            ?: return@withinReadAction
                    if (
                        Database.place.accepts(elementAtCaret) ||
                        Collection.place.accepts(elementAtCaret) ||
                        Field.place.accepts(elementAtCaret)
                    ) {
                        invokeInEdt {
                            val autoPopupController = AutoPopupController.getInstance(
                                event.editor.project!!
                            )
                            autoPopupController.scheduleAutoPopup(
                                event.editor,
                                CompletionType.BASIC,
                                null
                            )
                        }
                    }
                }
            }
        }
    }

    override fun beforeCharTyped(
        typedChar: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType
    ): Result {
        if (typedChar == '"') {
            events.tryEmit(AutocompletionEvent(file, editor))
        }

        return Result.CONTINUE
    }

    /**
     * @property file
     * @property editor
     */
    private data class AutocompletionEvent(
        val file: PsiFile,
        val editor: Editor
    )
}

/**
 * This class configures the confidence for MongoDB queries. Basically CompletionConfidence is used to check if we
 * should trigger completion based on the current element. We need this because by default, Java won't trigger
 * autocompletion on opening quotes.
 */
class MongoDbStringCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(
        editor: Editor,
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int
    ): ThreeState {
        if (contextElement is PsiJavaToken) {
            return ThreeState.NO
        }

        return ThreeState.YES
    }
}

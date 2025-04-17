package com.mongodb.jbplugin.ui.viewModel

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.getOrMaybeCreateUserData
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CaretView(val file: VirtualFile, val offset: Int)

data class EditorState(
    val focusedFile: VirtualFile? = null,
    val focusedFiles: List<VirtualFile>,
    val openFiles: List<VirtualFile>,
    val carets: List<CaretView>
) {
    companion object {
        fun default(): EditorState {
            return EditorState(null, emptyList(), emptyList(), emptyList())
        }
    }
}

private val allDialects: List<Dialect<PsiElement, Project>> = listOf(
    JavaDriverDialect,
    SpringAtQueryDialect,
    SpringCriteriaDialect
)

private const val MAX_TREE_DEPTH = 100

private val log = logger<CodeEditorViewModel>()

@Service(Service.Level.PROJECT)
class CodeEditorViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope,
) : FileEditorManagerListener, PsiModificationTracker.Listener, CaretListener {
    private val mutableEditorState = MutableStateFlow(EditorState.default())
    val editorState = mutableEditorState.asStateFlow()

    val inferredDatabase: String?
        get() = withinReadActionBlocking {
            val focusedFile = editorState.value.focusedFile ?: return@withinReadActionBlocking null
            val context = getDialectForFile(file = focusedFile)
                ?.connectionContextExtractor
                ?.gatherContext(this.project)
            context?.database
        }

    val selectedEditor: Editor?
        get() = (FileEditorManager.getInstance(this.project).selectedEditor as? TextEditor)?.editor

    init {
        val disposable = MdbPluginDisposable.getInstance(project)
        val messageBusConnection = project.messageBus.connect(disposable)
        val manager = FileEditorManager.getInstance(project)

        rebuildEditorState(manager)
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
        EditorFactory.getInstance().eventMulticaster.addCaretListener(this, disposable)
    }

    override fun modificationCountChanged() {
        val focusedFile = editorState.value.focusedFile
        if (focusedFile != null) {
            removeDialectsForFile(file = focusedFile)
        }
        rebuildEditorState(FileEditorManager.getInstance(project))
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        rebuildEditorState(source)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        rebuildEditorState(event.manager)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        rebuildEditorState(source)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        try {
            val allCarets = event.editor.caretModel.allCarets.map {
                CaretView(it.editor.virtualFile, it.offset)
            }
            val toEmit = mutableEditorState.value.copy(carets = allCarets)
            mutableEditorState.tryEmit(toEmit)
        } catch (e: Exception) {
            // There are a few edge cases where tracking the caret position bubbles an error in
            // the IDE for null virtualFile, so we catch that here and log it.
            // Unfortunately, it appears that we cannot log the exception as well because the
            // exception message also tries to access the virtualFile and fails again.
            log.warn(useLogMessage("Unable to track caretPositionChanged event").build())
        }
    }

    suspend fun reanalyzeRelevantEditors() {
        withContext(Dispatchers.IO) {
            withinReadAction {
                val allPsiFiles = editorState.value.focusedFiles.mapNotNull {
                    it.findPsiFile(project)
                }

                for (psiFile in allPsiFiles) {
                    InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }
            }
        }
    }

    suspend fun focusQueryInEditor(query: Node<PsiElement>, fileEditorManager: FileEditorManager? = null) {
        withContext(Dispatchers.EDT) {
            val vFile = query.source.containingFile.virtualFile

            val manager = fileEditorManager ?: FileEditorManager.getInstance(query.source.project)
            val editorOfFile = manager.selectedTextEditor?.takeIf { it.virtualFile == vFile }
                ?: manager.openTextEditor(OpenFileDescriptor(query.source.project, vFile, -1), true)
                ?: return@withContext

            editorOfFile.caretModel.moveToOffset(query.source.textOffset)
        }
    }

    fun queriesAtCaret(): List<Node<PsiElement>> {
        return mutableEditorState.value.carets.mapNotNull(::queryAtCaret)
    }

    private fun queryAtCaret(caret: CaretView): Node<PsiElement>? {
        val psiFile = caret.file.findPsiFile(project) ?: return null
        // iterate upwards until we find a query
        var currentPsiElement = psiFile.findElementAt(caret.offset)
        val queryService by project.service<CachedQueryService>()
        for (tries in 0..MAX_TREE_DEPTH) {
            if (currentPsiElement == null) {
                return null
            }

            val query = queryService.queryAt(currentPsiElement)
            if (query != null) {
                return query
            }
            currentPsiElement = currentPsiElement.parent
        }

        return null
    }

    suspend fun allProjectFiles(project: Project): List<VirtualFile> = withContext(Dispatchers.IO) {
        withinReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val javaFileType = FileTypeManager.getInstance().getStdFileType("JAVA")

            FileTypeIndex.getFiles(javaFileType, scope).filter {
                getDialectForFile(it) != null
            }
        }
    }

    private fun rebuildEditorState(manager: FileEditorManager) {
        coroutineScope.launch(Dispatchers.IO) {
            val (openFiles, focusedFiles, focusedFile) = withinReadAction {
                Triple(
                    manager.openFiles.toList().distinctBy { it.canonicalPath },
                    manager.selectedEditors
                        .mapNotNull { it.file }
                        .distinctBy { it.canonicalPath }.toList(),
                    manager.selectedEditor?.file,
                )
            }

            val toEmit = mutableEditorState.value.copy(
                openFiles = openFiles,
                focusedFiles = focusedFiles,
                focusedFile = focusedFile,
            )

            mutableEditorState.emit(toEmit)

            withinReadAction {
                for (file in openFiles) {
                    getDialectForFile(file)
                }
            }
        }
    }

    private fun removeDialectsForFile(file: VirtualFile) {
        file.removeUserData(Keys.identifiedDialects)
    }

    private fun getDialectForFile(file: VirtualFile): Dialect<PsiElement, Project>? {
        return file.getOrMaybeCreateUserData(Keys.identifiedDialects) {
            try {
                val psiFile = file.findPsiFile(this.project)
                    ?: throw Exception("PsiFile not found")
                allDialects.filter { it.isUsableForSource(psiFile) }
            } catch (exception: Exception) {
                log.warn(
                    useLogMessage("Could not identify dialects for ${file.path}").build(),
                    exception,
                )
                emptyList()
            }
        }?.firstOrNull()
    }
}

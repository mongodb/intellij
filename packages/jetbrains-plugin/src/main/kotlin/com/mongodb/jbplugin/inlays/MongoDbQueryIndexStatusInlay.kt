/**
 * Adding an icon to an inlay forces us to use an experimental API.
 */
@file:Suppress("UnstableApiUsage")

package com.mongodb.jbplugin.inlays

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.MouseButton.Left
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan.CollectionScan
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan.IndexScan
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan.IneffectiveIndexUsage
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan.NotRun
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import com.mongodb.jbplugin.linting.correctness.isNamespaceAvailableInCluster
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.FULL
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import com.mongodb.jbplugin.mql.parser.components.allFiltersRecursively
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.settings.pluginSetting
import com.mongodb.jbplugin.ui.viewModel.InspectionsViewModel
import com.mongodb.jbplugin.ui.viewModel.SidePanelViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Cursor
import javax.swing.JComponent

class MongoDbQueryIndexStatusInlay(
    private val coroutineScope: CoroutineScope,
) : InlayHintsProvider<Unit> {
    override val name: String = InspectionsAndInlaysMessages.message("inlay.indexing.name")
    override val key: SettingsKey<Unit> = SettingsKey("com.mongodb.jbplugin.MongoDbQueryIndexStatusInlay")
    override val previewText: String = "" // We don't show a preview

    override fun createConfigurable(settings: Unit): ImmediateConfigurable = NoConfigurable
    override fun createSettings() {}

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Unit,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        return QueriesInFileCollector(coroutineScope).takeIf {
            file.dialect != null &&
                file.dataSource?.isConnected() == true
        }
    }
}

private object NoConfigurable : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent {
        return JewelComposePanel { }
    }
}

internal class QueriesInFileCollector(private val coroutineScope: CoroutineScope) : InlayHintsCollector {
    override fun collect(
        element: PsiElement,
        editor: Editor,
        sink: InlayHintsSink
    ): Boolean {
        // this might happen if we disconnected while the collection is happening
        val dataSource = element.containingFile.dataSource ?: return false

        val isFullExplainPlanEnabled by pluginSetting { ::isFullExplainPlanEnabled }
        val explainPlanType = if (isFullExplainPlanEnabled) {
            FULL
        } else {
            SAFE
        }

        val queryService by element.project.service<CachedQueryService>()
        val query = queryService.queryAt(element) ?: return true
        val readModelProvider by element.project.service<DataGripBasedReadModelProvider>()

        if (!query.isSupportedBlocking()) {
            return false
        }

        val queryWithExplainPlan = query.with(HasExplain(explainPlanType))
        val queryContext = QueryContext.empty(automaticallyRun = true)

        val collectionReference = query.component<HasCollectionReference<PsiElement>>()?.reference

        coroutineScope.launch(Dispatchers.IO) {
            val queryHasNoFilters = when (
                val allFilters = allFiltersRecursively<PsiElement>().parse(query)
            ) {
                is Either.Left -> true
                is Either.Right -> allFilters.value.isEmpty()
            }
            val skipInlayDecoration =
                queryHasNoFilters ||
                    collectionReference !is HasCollectionReference.Known ||
                    !collectionReference.namespace.isValid ||
                    !collectionReference.namespace.isNamespaceAvailableInCluster(
                        dataSource = dataSource,
                        readModelProvider = readModelProvider,
                    )

            if (skipInlayDecoration) {
                return@launch
            }

            val explainPlan = runCatching {
                readModelProvider.slice(
                    dataSource,
                    ExplainQuery.Slice(
                        queryWithExplainPlan,
                        queryContext
                    )
                ) {
                    withContext(Dispatchers.EDT) {
                        InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
                        DaemonCodeAnalyzer.getInstance(element.project).restart(
                            element.containingFile
                        )
                    }
                }.explainPlan
            }.getOrDefault(NotRun)

            val (icon, text, tooltip) = when (explainPlan) {
                CollectionScan ->
                    Triple(
                        Icons.indexWarningIcon,
                        InspectionsAndInlaysMessages.message("inlay.indexing.coll-scan.text"),
                        InspectionsAndInlaysMessages.message("inlay.indexing.coll-scan.tooltip")
                    )
                is IndexScan ->
                    Triple(
                        Icons.indexOkIcon,
                        InspectionsAndInlaysMessages.message("inlay.indexing.index-scan.text"),
                        explainPlan.indexName
                    )
                is IneffectiveIndexUsage ->
                    Triple(
                        Icons.indexWarningIcon,
                        InspectionsAndInlaysMessages.message("inlay.indexing.ineffective-index-scan.text"),
                        explainPlan.indexName
                    )
                NotRun ->
                    Triple(
                        Icons.queryNotRunIcon,
                        InspectionsAndInlaysMessages.message("inlay.indexing.not-run.text"),
                        InspectionsAndInlaysMessages.message("inlay.indexing.not-run.tooltip")
                    )
            }

            val inlayFactory = PresentationFactory(editor)
            val inlay = inlayFactory.seq(
                inlayFactory.smallScaledIcon(icon),
                inlayFactory.text(text),
            ).let {
                inlayFactory.withTooltip(tooltip, it)
            }.let {
                inlayFactory.withCursorOnHover(it, Cursor(Cursor.HAND_CURSOR))
            }.let {
                inlayFactory.onClick(it, Left) { _, _ ->
                    val sidePanelViewModel by element.project.service<SidePanelViewModel>()
                    val inspectionsViewModel by element.project.service<InspectionsViewModel>()

                    sidePanelViewModel.withOpenSidePanel {
                        inspectionsViewModel.openCategory(PERFORMANCE)
                    }
                }
            }

            sink.addInlineElement(
                query.source.textOffset,
                true,
                inlay,
                true
            )
        }

        return true
    }
}

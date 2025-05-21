package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.meta.inEdt
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.settings.pluginSetting
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = logger<CachedQueryService>()

/**
 * Attempts to parse a query for a PsiElement if there is one, given the dialect of the current
 * file. Caches the result inside the PsiElement metadata and only reparses the query if that
 * element or it's children change.
 *
 * It might return null if there is no query to parse.
 *
 * @property coroutineScope
 */
@Service(Service.Level.PROJECT)
class CachedQueryService(
    val project: Project,
    val coroutineScope: CoroutineScope
) : SiblingQueriesFinder<PsiElement> {
    private val queryCacheKey = Key.create<CachedValue<Node<PsiElement>>>("QueryCache")
    private val cachedQueriesByNamespace:
        MutableMap<Namespace, Set<Node<PsiElement>>> = mutableMapOf()
    private val rwLock: ReentrantReadWriteLock = ReentrantReadWriteLock(true)

    fun queryAt(expression: PsiElement): Node<PsiElement>? {
        val fileInExpression = getParentOfType(expression, PsiFile::class.java) ?: return null
        val dataSource = fileInExpression.dataSource

        val dialect = expression.containingFile.dialect ?: return null
        if (!dialect.parser.isCandidateForQuery(expression)) {
            return null
        }

        val attachment = dialect.parser.attachment(expression)
        val psiManager = PsiManager.getInstance(expression.project)
        if (!psiManager.areElementsEquivalent(expression, attachment)) {
            return null
        }

        val cacheManager = CachedValuesManager.getManager(attachment.project)
        attachment.getUserData(queryCacheKey)?.let {
            return decorateWithMetadata(dataSource, attachment.getUserData(queryCacheKey)!!.value)
        }

        val connectionStateViewModel by project.service<ConnectionStateViewModel>()
        val cachedValue = cacheManager.createCachedValue {
            val parsedAst = runCatching { dialect.parser.parse(expression) }.getOrNull()
            val namespaceOfQuery = extractNamespaceOfQuery(parsedAst)

            if (parsedAst != null && namespaceOfQuery != null) {
                // we get an exclusive lock because we are in IntelliJ's parser thread. Essentially,
                // this is the only thread who should update the cached queries.
                // We do this in the cached value because we want to update the cached queries
                // every time a query goes stale.
                rwLock.write {
                    // here we have an exclusive lock, let's first do some housekeeping.
                    // clear all stale references (queries that do not exist anymore)
                    for (entry in cachedQueriesByNamespace.entries) {
                        val queries = entry.value.filter {
                            runCatching { it.source.containingFile }.isSuccess
                        }.toSet()

                        entry.setValue(queries)
                    }

                    // now add the current query to the cache
                    cachedQueriesByNamespace.compute(namespaceOfQuery) { _, queries ->
                        (queries ?: emptySet()) + parsedAst
                    }
                }
            }

            CachedValueProvider.Result.create(parsedAst, attachment, connectionStateViewModel)
        }

        attachment.putUserData(queryCacheKey, cachedValue)
        return decorateWithMetadata(dataSource, attachment.getUserData(queryCacheKey)!!.value)
    }

    override fun allSiblingsOf(query: Node<PsiElement>): Array<Node<PsiElement>> {
        val collRef =
            query.component<HasCollectionReference<PsiElement>>()?.reference as? Known
                ?: return emptyArray()

        val psiManager = PsiManager.getInstance(query.source.project)

        // Request a read lock. In this case, we don't block other reader threads, but we will get blocked
        // when someone else requests this lock in write mode. This will only happen when there is a change
        // in a query from the editor and IntelliJs parser kicks in.
        return rwLock.read {
            val allQueriesForNamespace = cachedQueriesByNamespace.getOrDefault(
                collRef.namespace,
                emptySet()
            )

            // filter out all stale references and then decorate with whatever metadata is relevant
            // from the query source file. Return a copy of the set as an array so further modifications
            // do not affect the returned value.
            // In addition, filter out ourselves from the array, as we don't need to handle the query twice.
            allQueriesForNamespace
                .filter { !psiManager.areElementsEquivalent(query.source, it.source) }
                .map { decorateWithMetadata(it.source.containingFile.dataSource, it) }
                .distinct()
                .toTypedArray()
        }
    }

    /**
     * We need this because in Spring Data we need the context of the project to get the actual
     * namespace (the database is either in the application.yaml or in the toolbar).
     */
    private fun extractNamespaceOfQuery(query: Node<PsiElement>?): Namespace? {
        if (query == null) {
            return null
        }

        val ref = query.component<HasCollectionReference<PsiElement>>()?.reference as? Known
        if (ref == null && query.source.containingFile.database != null) {
            val decoratedQuery = query.queryWithOverwrittenDatabase(
                query.source.containingFile.database!!
            )
            val collRef = decoratedQuery.component<HasCollectionReference<PsiElement>>()?.reference as? Known
            return collRef?.namespace
        } else {
            return ref?.namespace
        }
    }
    private fun decorateWithMetadata(
        dataSource: LocalDataSource?,
        query: Node<PsiElement>
    ): Node<PsiElement> {
        val queryWithDb = query.source.containingFile.database?.let {
            query.queryWithOverwrittenDatabase(it)
        } ?: query

        if (inEdt()) {
            logger.info(
                useLogMessage(
                    """`decorateWithMetadata` should not be used in the EDT thread.
                  |Breaking into a fast-path that does not query MongoDB.
                  |This avoids freezes in the IDE.
                    """.trimMargin()
                ).build()
            )

            return queryWithDb
        }

        return runBlocking {
            if (dataSource == null || !dataSource.isConnected()) {
                return@runBlocking queryWithDb
            }

            val readModel by query.source.project.service<DataGripBasedReadModelProvider>()
            val buildInfo = try {
                readModel.slice(dataSource, BuildInfo.Slice)
            } catch (e: Exception) {
                logger.warn(
                    useLogMessage("Failed to get build info for the current cluster.").build(),
                    e
                )
                null
            } ?: return@runBlocking queryWithDb

            val queryWithTargetCluster = queryWithDb.withTargetCluster(
                HasTargetCluster(Version.parse(buildInfo.version))
            )

            val knownReference = queryWithTargetCluster.component<HasCollectionReference<*>>()?.reference as? Known<*>
                ?: return@runBlocking queryWithTargetCluster

            val sampleSize by pluginSetting { ::sampleSize }
            val collectionSchema = try {
                readModel.slice(
                    dataSource,
                    GetCollectionSchema.Slice(
                        knownReference.namespace,
                        sampleSize
                    )
                )
            } catch (e: Exception) {
                logger.warn(
                    useLogMessage("Failed to get collection schema for the current cluster.").build(),
                    e
                )
                null
            } ?: return@runBlocking queryWithTargetCluster

            queryWithTargetCluster.queryWithInjectedCollectionSchema(
                collectionSchema.schema
            )
        }
    }
}

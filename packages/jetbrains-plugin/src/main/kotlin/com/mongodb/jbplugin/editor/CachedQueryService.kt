package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.settings.pluginSetting
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import java.lang.ref.WeakReference
import java.util.Objects
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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
    val coroutineScope: CoroutineScope
) : SiblingQueriesFinder<PsiElement> {
    private val queryCacheKey = Key.create<CachedValue<Node<PsiElement>>>("QueryCache")
    private val cachedQueriesByNamespace:
        MutableMap<Namespace, Set<WeakReference<Node<PsiElement>>>> = mutableMapOf()
    private val rwLock: ReentrantReadWriteLock = ReentrantReadWriteLock(true)

    fun queryAt(expression: PsiElement): Node<PsiElement>? {
        val fileInExpression =
            PsiTreeUtil.getParentOfType(expression, PsiFile::class.java) ?: return null
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

        val toolbar = attachment.project.getToolbarModel()
        val cachedValue = cacheManager.createCachedValue {
            val parsedAst = dialect.parser.parse(expression)
            val namespaceOfQuery = extractNamespaceOfQuery(parsedAst)

            if (namespaceOfQuery != null) {
                // we get an exclusive lock because we are in IntelliJ's parser thread. Essentially,
                // this is the only thread who should update the cached queries.
                // We do this in the cached value because we want to update the cached queries
                // every time a query goes stale.
                rwLock.write {
                    // here we have an exclusive lock, let's first do some housekeeping.
                    // clear all stale references (queries that do not exist anymore)
                    for (entry in cachedQueriesByNamespace.entries) {
                        val queries = entry.value
                        entry.setValue(
                            queries.filter { it.get() != null }
                                .filter {
                                    runCatching { it.get()?.source?.containingFile }.isSuccess
                                }
                                .distinctBy { it.get()?.source?.textOffset }.toSet()
                        )
                    }

                    // now add the current query to the cache
                    cachedQueriesByNamespace.compute(namespaceOfQuery) { ns, queries ->
                        (queries ?: emptySet()) + WeakReference(parsedAst)
                    }
                }
            }

            CachedValueProvider.Result.create(parsedAst, attachment, toolbar)
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
        return rwLock.read<Array<Node<PsiElement>>> {
            val allQueriesForNamespace = cachedQueriesByNamespace.getOrDefault(
                collRef.namespace,
                emptySet()
            )

            // Sometimes we can have a query twice because the old psi element is not
            // deleted. This class uses Psi element equivalence to get rid of
            // duplicates.
            class EquivalenceBasedQuery(val query: Node<PsiElement>) {
                override fun equals(other: Any?): Boolean {
                    return psiManager.areElementsEquivalent(
                        query.source,
                        other as? PsiElement
                    )
                }

                override fun hashCode(): Int {
                    return Objects.hash(query.components)
                }
            }

            // filter out all stale references and then decorate with whatever metadata is relevant
            // from the query source file. Return a copy of the set as an array so further modifications
            // do not affect the returned value.
            // In addition, filter out ourselves from the array, as we don't need to handle the query twice.
            allQueriesForNamespace
                .mapNotNull { it.get() }
                .map { EquivalenceBasedQuery(it) }
                .distinct()
                .map { it.query }
                .filter { !psiManager.areElementsEquivalent(it.source, query.source) }
                .map { decorateWithMetadata(it.source.containingFile.dataSource, it) }
                .toTypedArray()
        }
    }

    /**
     * We need this because in Spring Data we need the context of the project to get the actual
     * namespace (the database is either in the application.yaml or in the toolbar).
     */
    private fun extractNamespaceOfQuery(query: Node<PsiElement>): Namespace? {
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

        return runCatching {
            if (dataSource != null && dataSource.isConnected()) {
                val readModel by query.source.project.service<DataGripBasedReadModelProvider>()
                val buildInfo = readModel.slice(dataSource, BuildInfo.Slice)

                queryWithDb.withTargetCluster(
                    HasTargetCluster(Version.parse(buildInfo.version))
                )

                val knownReference = queryWithDb.component<HasCollectionReference<*>>()?.reference as? Known<*>
                if (knownReference != null) {
                    val sampleSize by pluginSetting { ::sampleSize }
                    val collectionSchema = readModel.slice(
                        dataSource,
                        GetCollectionSchema.Slice(knownReference.namespace, sampleSize)
                    )
                    queryWithDb.queryWithInjectedCollectionSchema(
                        collectionSchema.schema
                    )
                } else {
                    queryWithDb
                }
            } else {
                queryWithDb
            }
        }.getOrDefault(queryWithDb)
    }
}

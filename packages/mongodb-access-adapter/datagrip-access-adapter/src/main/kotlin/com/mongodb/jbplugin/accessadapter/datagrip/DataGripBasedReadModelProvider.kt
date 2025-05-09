/**
 * Represents a service that allows access to a MongoDB cluster
 * configured through a DataGrip DataSource.
 */

package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.Slice
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration.Companion.seconds

private typealias DriverFactory = (Project, LocalDataSource) -> MongoDbDriver

/**
 * The service to be injected to access MongoDB. Usually you will use
 * it like this:
 *
 * ```kt
 * val readModelProvider = event.project!!.getService(DataGripBasedReadModelProvider::class.java)
 * val dataSource = event.dataContext.getData(PlatformDataKeys.PSI_ELEMENT) as DbDataSource
 * val buildInfo = readModelProvider.slice(dataSource.localDataSource!!, BuildInfoSlice)
 * ```
 *
 * It will aggressively cache data at the data source level, to avoid hitting MongoDB. Also, the provided
 * driver is very slow, so it's better to avoid querying on performance sensitive contexts.
 *
 * @param project
 */
@Service(Service.Level.PROJECT)
class DataGripBasedReadModelProvider(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : MongoDbReadModelProvider<LocalDataSource> {
    var driverFactory: DriverFactory = { project, dataSource ->
        DataGripMongoDbDriver(project, dataSource)
    }

    @VisibleForTesting
    internal var wasCached: Boolean = false

    /**
     * LocalDataSource implements ModificationTracker, which is a mechanism by which a consumer
     * of the data source can detect if the metadata of the LocalDataSource has changed. This
     * modification is similar to a versioning system, it contains a Long value that contains
     * the current version, and on each change it just increases by 1.
     *
     * We are using the `modificationCount` as a modification stamp: if the modification count
     * did not change *and we already have a result from that slice* we can just return the cached
     * result. Otherwise, we call the slice and store the current modificationCount along with the
     * result of the query.
     */
    private val cachedValues = mutableMapOf<String, Pair<Long, *>>()
    private val rwLock = ReentrantReadWriteLock(true)

    override suspend fun <T : Any> slice(
        dataSource: LocalDataSource,
        slice: Slice<T>,
        onCacheRecalculation: (suspend (T) -> Unit)?,
    ): T {
        return withContext(Dispatchers.IO) {
            withTimeout(10.seconds) {
                val entryKey = "${dataSource.uniqueId}/${slice.id}"

                rwLock.read {
                    wasCached = true
                    if (cachedValues.containsKey(entryKey)) {
                        val entry = cachedValues[entryKey]!!
                        if (entry.first < dataSource.modificationCount) {
                            refreshCache(entryKey, slice, dataSource)
                        }
                    } else {
                        refreshCache(entryKey, slice, dataSource)
                    }

                    val result = cachedValues[entryKey]?.second as T
                    if (!wasCached && onCacheRecalculation != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            onCacheRecalculation(result)
                        }
                    }
                    result
                }
            }
        }
    }

    private suspend fun <T : Any> refreshCache(entry: String, slice: Slice<T>, dataSource: LocalDataSource) {
        rwLock.write {
            val driver = driverFactory(project, dataSource)
            val newValue = runCatching { slice.queryUsingDriver(driver) }.getOrNull()
            wasCached = false
            cachedValues[entry] = dataSource.modificationCount to newValue
        }
    }
}

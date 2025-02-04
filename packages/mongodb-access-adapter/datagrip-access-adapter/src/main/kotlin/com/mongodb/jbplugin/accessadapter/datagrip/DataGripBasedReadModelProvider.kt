/**
 * Represents a service that allows access to a MongoDB cluster
 * configured through a DataGrip DataSource.
 */

package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.Slice
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriver
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

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
) : MongoDbReadModelProvider<LocalDataSource> {
    var driverFactory: DriverFactory = { project, dataSource ->
        DataGripMongoDbDriver(project, dataSource)
    }

    private val cachedValues: ConcurrentMap<String, Pair<Long, *>> = ConcurrentHashMap()

    override fun <T : Any> slice(
        dataSource: LocalDataSource,
        slice: Slice<T>,
    ): T {
        return cachedValues.compute("${dataSource.uniqueId}/${slice.id}") { key, data ->
            val modificationStamp = data?.first ?: -1
            val cachedValue = data?.second

            if (cachedValue == null || modificationStamp < dataSource.modificationCount) {
                val driver = driverFactory(project, dataSource)
                val newValue = runCatching {
                    runBlocking { slice.queryUsingDriver(driver) }
                }.getOrNull()
                dataSource.modificationCount to newValue
            } else {
                modificationStamp to cachedValue
            }
        }?.second!! as T
    }
}

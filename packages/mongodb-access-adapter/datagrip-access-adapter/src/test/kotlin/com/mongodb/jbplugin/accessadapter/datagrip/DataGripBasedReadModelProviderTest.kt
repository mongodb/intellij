package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

@IntegrationTest
class DataGripBasedReadModelProviderTest {
    @Test
    fun `can query a slice and returns the result`(
        project: Project,
        dataSource: LocalDataSource,
        version: MongoDbVersion
    ) = runTest {
        val service = project.getService(DataGripBasedReadModelProvider::class.java)
        val info = service.slice(dataSource, BuildInfo.Slice)

        assertEquals(version.versionString, info.version)
    }

    @Test
    fun `can cache a query for the same slice if the data source did not change`(
        project: Project,
        dataSource: LocalDataSource,
        version: MongoDbVersion
    ) = runTest {
        val service = project.getService(DataGripBasedReadModelProvider::class.java)

        val info1 = service.slice(dataSource, BuildInfo.Slice)
        val info2 = service.slice(dataSource, BuildInfo.Slice)

        assertEquals(version.versionString, info1.version)
        assertEquals(info1, info2)
        assertEquals(service.wasCached, true)
    }

    @Test
    fun `does not cache the query for the same slice if the data source changed`(
        project: Project,
        dataSource: LocalDataSource,
    ) = runTest {
        val service = project.getService(DataGripBasedReadModelProvider::class.java)

        service.slice(dataSource, BuildInfo.Slice)
        dataSource.incModificationCount()
        service.slice(dataSource, BuildInfo.Slice)

        assertEquals(service.wasCached, false)
    }

    @Test
    fun `calls onCacheRecalculation when cache is recalculated`(
        project: Project,
        dataSource: LocalDataSource,
    ) = runTest {
        val service = project.getService(DataGripBasedReadModelProvider::class.java)
        val wasRecalculated = AtomicBoolean(false)

        // Cache will be calculated on the first call to Slice
        service.slice(dataSource, ListDatabases.Slice) {
            wasRecalculated.set(true)
        }

        eventually {
            assertEquals(true, wasRecalculated.get())
        }
    }
}

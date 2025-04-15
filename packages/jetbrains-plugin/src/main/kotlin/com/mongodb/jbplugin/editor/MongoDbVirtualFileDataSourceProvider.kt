package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.VirtualFileDataSourceProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState

private const val KEY_PREFIX = "com.mongodb.jbplugin"

val PsiFile.dataSource: LocalDataSource?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDataSource(
                project,
                virtualFile,
            )?.localDataSource
    }.getOrNull()

val PsiFile.database: String?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDatabase(project)
            .takeIf {
                dialect is SpringCriteriaDialect || dialect is SpringAtQueryDialect
            }
    }.getOrNull()

val PsiFile.dialect: Dialect<PsiElement, Project>?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDialect(
                virtualFile,
            )
    }.getOrNull()

/**
 * Returns the data source, if attached to the editor through the MongoDB Plugin.
 */
class MongoDbVirtualFileDataSourceProvider : VirtualFileDataSourceProvider() {
    object Keys {
        internal val attachedDialect: Key<Dialect<PsiElement, Project>?> = Key.create(
            "$KEY_PREFIX.AttachedDialect"
        )
    }

    override fun getDataSource(
        project: Project,
        file: VirtualFile,
    ): DbDataSource? {
        val facade = DbPsiFacade.getInstance(project)
        val connectionViewModel by project.service<ConnectionStateViewModel>()

        val (_, selectedConnection, selectedConnectionState) =
            connectionViewModel.connectionState.value
        return when (selectedConnectionState) {
            is SelectedConnectionState.Connected -> facade.findDataSource(
                selectedConnection?.uniqueId
            )
            else -> null
        }
    }

    fun getDatabase(
        project: Project,
    ): String? {
        val connectionViewModel by project.service<ConnectionStateViewModel>()
        val (_, selectedDatabase) =
            connectionViewModel.databaseState.value
        return selectedDatabase
    }

    fun getDialect(
        file: VirtualFile
    ): Dialect<PsiElement, Project>? = file.getUserData(Keys.attachedDialect)
}

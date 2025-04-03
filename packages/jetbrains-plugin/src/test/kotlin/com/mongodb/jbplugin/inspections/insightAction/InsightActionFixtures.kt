package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun Project.aQuery(): Node<PsiElement> {
    val query = mock<Node<PsiElement>>()
    val source = mock<PsiElement>()
    val containingFile = mock<PsiFile>()
    val backingVirtualFile = mock<VirtualFile>()

    whenever(source.project).thenReturn(this)
    whenever(query.source).thenReturn(source)
    whenever(source.containingFile).thenReturn(containingFile)
    whenever(containingFile.virtualFile).thenReturn(backingVirtualFile)
    whenever(containingFile.project).thenReturn(this)

    return query
}

internal fun Project.connectedTo(
    dataSource: LocalDataSource,
) {
    val uniqueId = dataSource.uniqueId

    val dbDataSource = mock<DbDataSource>()
    whenever(dbDataSource.uniqueId).thenReturn(uniqueId)
    whenever(dbDataSource.localDataSource).thenReturn(dataSource)

    val dbPsiFacade = mock<DbPsiFacade>()
    whenever(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)

    withMockedService(dbPsiFacade)

    val viewModel = mock<ConnectionStateViewModel>()
    val connectionState = MutableStateFlow(
        ConnectionState(
            connections = listOf(dataSource),
            selectedConnection = dataSource,
            selectedConnectionState = SelectedConnectionState.Connected(dataSource),
        )
    )

    whenever(viewModel.connectionState).thenReturn(connectionState)

    withMockedService(viewModel)
}

package com.mongodb.jbplugin.editor.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.useLogMessage
import io.ktor.util.collections.*

private val log = logger<MdbPluginDisposable>()

/**
 * Since our Plugin acts mostly on Project level and initialises different resources / sets up subscriptions, some of
 * them might require clean up. Services initialised by IntelliJ are automatically cleaned up but manually set up
 * subscriptions and acquired resources needs a parent disposable to tie their lifecycle to. This class acts our
 * disposable parent for such subscriptions and resources which we may want to clean up when a project is closed or the
 * plugin is unloaded
 */
@Service(Service.Level.PROJECT)
class MdbPluginDisposable : Disposable {
    private val onDisposeListeners = ConcurrentSet<() -> Unit>()

    /**
     * Disposable interface that gets called when this service is being disposed
     */
    override fun dispose() {
        val listeners = onDisposeListeners.toList()
        onDisposeListeners.clear()
        for (listener in listeners) {
            try {
                listener()
            } catch (exception: Exception) {
                log.warn(
                    useLogMessage("Exception while running an onDispose listener").build(),
                    exception,
                )
            }
        }
    }

    companion object {
        /**
         * Retrieves an instance of the MdbPluginDisposable from the project
         *
         * @param project
         * @return
         */
        fun getInstance(
            project: Project
        ): MdbPluginDisposable = project.getService(MdbPluginDisposable::class.java)
    }
}

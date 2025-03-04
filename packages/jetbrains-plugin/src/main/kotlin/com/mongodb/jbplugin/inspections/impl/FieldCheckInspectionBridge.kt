/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.IntelliJBasedInspectionHolder
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.linting.FieldCheckingLinter
import com.mongodb.jbplugin.linting.FieldCheckingSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class FieldCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        FieldCheckLinterInspection,
    ) {
    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder
        )

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.TYPE_MISMATCH,
            problemsHolder
        )
    }
}

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object FieldCheckLinterInspection : MongoDbInspection {
    private val linter = FieldCheckingLinter<LocalDataSource>()

    override suspend fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource,
        problems: IntelliJBasedInspectionHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val sampleSize by pluginSetting { ::sampleSize }

        linter.run(
            query,
            problems,
            FieldCheckingSettings(
                dataSource,
                readModelProvider,
                sampleSize
            )
        )
    }
}

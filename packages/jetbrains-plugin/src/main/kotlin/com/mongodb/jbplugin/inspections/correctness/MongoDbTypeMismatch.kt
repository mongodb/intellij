package com.mongodb.jbplugin.inspections.correctness

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.correctness.TypeMismatchInspection
import com.mongodb.jbplugin.linting.correctness.TypeMismatchInspectionSettings
import com.mongodb.jbplugin.meta.dialectByName
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class MongoDbTypeMismatchGlobalTool : AbstractMongoDbInspectionGlobalTool(TypeMismatch)
class MongoDbTypeMismatch(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridgeV2<
    TypeMismatchInspectionSettings<LocalDataSource>,
    TypeMismatch
    >(
    coroutineScope,
    TypeMismatchInspection(),
    TypeMismatch
) {
    override fun buildSettings(query: Node<PsiElement>): TypeMismatchInspectionSettings<LocalDataSource> {
        val documentsSampleSize by pluginSetting { ::sampleSize }
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val dataSource = query.source.containingFile.dataSource!!
        val dialectName = query.component<HasSourceDialect>()?.name ?: HasSourceDialect.DialectName.JAVA_DRIVER
        val dialect = dialectByName(dialectName)

        return TypeMismatchInspectionSettings(
            dataSource,
            readModelProvider,
            documentsSampleSize,
            dialect.formatter::formatType
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, TypeMismatch>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.TYPE_MISMATCH,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, TypeMismatch>>) {
        val probe by service<InspectionStatusChangedProbe>()

        probe.finishedProcessingInspections(
          TelemetryEvent.InspectionStatusChangeEvent.InspectionType.TYPE_MISMATCH,
          queryInsights
        )
    }
}

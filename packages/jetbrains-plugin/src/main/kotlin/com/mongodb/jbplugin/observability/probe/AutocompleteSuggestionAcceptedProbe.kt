package com.mongodb.jbplugin.observability.probe

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand.CommandType
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours

private val logger: Logger = logger<AutocompleteSuggestionAcceptedProbe>()

/**
 * This probe is emitted when an autocomplete suggestion is emitted. However, events are aggregated
 * and sent hourly to Segment.
 *
 * @param cs
 */
@Service
class AutocompleteSuggestionAcceptedProbe(
    cs: CoroutineScope,
) : AppLifecycleListener {
    private val telemetryJob: Job
    private val events: CopyOnWriteArrayList<SuggestionEvent>

    init {
        ApplicationManager
            .getApplication()
            .messageBus
            .connect()
            .subscribe(AppLifecycleListener.TOPIC, this)

        telemetryJob =
            cs.launch {
                telemetryLoop()
            }

        events = CopyOnWriteArrayList()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        telemetryJob.cancel()
        sendEvents()
    }

    fun databaseCompletionAccepted(dialect: Dialect<*, *>) {
        events.add(
            SuggestionEvent(
                dialect,
                SuggestionEvent.SuggestionEventType.DATABASE,
                CommandType.UNKNOWN,
            )
        )
    }

    fun collectionCompletionAccepted(dialect: Dialect<*, *>) {
        events.add(
            SuggestionEvent(
                dialect,
                SuggestionEvent.SuggestionEventType.COLLECTION,
                CommandType.UNKNOWN,
            )
        )
    }

    fun fieldCompletionAccepted(dialect: Dialect<*, *>, commandType: CommandType) {
        events.add(
            SuggestionEvent(
                dialect,
                SuggestionEvent.SuggestionEventType.FIELD,
                commandType
            )
        )
    }

    private suspend fun telemetryLoop(): Unit =
        withContext(Dispatchers.IO) {
            while (true) {
                // if it fails, ignore, we will retry in one hour
                runCatching {
                    sendEvents()
                }
                delay(1.hours)
            }
        }

    internal fun sendEvents() {
        val listCopy = events.toList()
        events.clear()

        val telemetry by service<TelemetryService>()

        listCopy
            .groupingBy {
                Triple(it.dialect, it.type, it.commandType)
            }
            .eachCount()
            .map {
                TelemetryEvent.AutocompleteGroupEvent(
                    dialectName(it.key.first),
                    it.key.second.publicName,
                    it.key.third.canonical,
                    it.value
                )
            }
            .sortedBy { it.name }
            .forEach {
                telemetry.sendEvent(it)

                logger.info(
                    useLogMessage("Autocomplete suggestion aggregated.")
                        .mergeTelemetryEventProperties(it)
                        .build(),
                )
            }
    }

    private fun dialectName(dialect: Dialect<*, *>): HasSourceDialect.DialectName {
        if (dialect == JavaDriverDialect) {
            return HasSourceDialect.DialectName.JAVA_DRIVER
        } else if (dialect == SpringCriteriaDialect) {
            return HasSourceDialect.DialectName.SPRING_CRITERIA
        } else if (dialect == SpringAtQueryDialect) {
            return HasSourceDialect.DialectName.SPRING_QUERY
        }

        return HasSourceDialect.DialectName.UNKNOWN
    }

    /**
     * @property dialect
     * @property type
     */
    private data class SuggestionEvent(
        val dialect: Dialect<*, *>,
        val type: SuggestionEventType,
        val commandType: CommandType,
    ) {
        /**
         * @property publicName
         */
        enum class SuggestionEventType(
            val publicName: String,
        ) {
            DATABASE("database"),
            COLLECTION("collection"),
            FIELD("field"),
        }
    }
}

/**
 * This module is responsible for adapting our autocomplete engine to IntelliJ's platform.
 */

package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.canBeFieldName
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.guessDatabaseAndCollection
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.isCollectionReference
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.isConnected
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.isDatabaseReference
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.toLookupElement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.database
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.observability.probe.AutocompleteSuggestionAcceptedProbe
import kotlin.collections.emptyList
import kotlin.collections.map

/**
 * This class connects our completion engine with IntelliJ's systems.
 */
class MongoDbCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, Database.place, Database.Provider)
        extend(CompletionType.BASIC, Collection.place, Collection.Provider)
        extend(CompletionType.BASIC, Field.place, Field.Provider)
    }
}

private object Database {
    val place: ElementPattern<PsiElement> =
        psiElement()
            .and(isConnected())
            .and(isDatabaseReference())

    object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val dataSource = parameters.originalFile.dataSource!!
            val readModelProvider =
                parameters.originalFile.project.getService(
                    DataGripBasedReadModelProvider::class.java,
                )

            val completions =
                Autocompletion.autocompleteDatabases(
                    dataSource,
                    readModelProvider,
                ) as? AutocompletionResult.Successful

            val lookupEntries = completions?.entries?.map { it.toLookupElement(JavaDriverDialect) } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

private object Collection {
    val place: ElementPattern<PsiElement> =
        psiElement()
            .and(isConnected())
            .and(isCollectionReference())

    object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val dataSource = parameters.originalFile.dataSource!!
            val (database, _) = guessDatabaseAndCollection(parameters.originalPosition!!)

            database ?: return

            val readModelProvider =
                parameters.originalFile.project.getService(
                    DataGripBasedReadModelProvider::class.java,
                )

            val completions =
                Autocompletion.autocompleteCollections(
                    dataSource,
                    readModelProvider,
                    database,
                ) as? AutocompletionResult.Successful

            val lookupEntries = completions?.entries?.map { it.toLookupElement(JavaDriverDialect) } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

private object Field {
    val place: ElementPattern<PsiElement> =
        psiElement()
            .and(isConnected())
            .and(canBeFieldName())

    object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val dataSource = parameters.originalFile.dataSource!!
            val (database, collection) = guessDatabaseAndCollection(parameters.originalPosition!!)

            if (database == null || collection == null) {
                return
            }
            val readModelProvider =
                parameters.originalFile.project.getService(
                    DataGripBasedReadModelProvider::class.java,
                )

            val completions =
                Autocompletion.autocompleteFields(
                    dataSource,
                    readModelProvider,
                    Namespace(database, collection),
                ) as? AutocompletionResult.Successful

            val lookupEntries = completions?.entries?.map { it.toLookupElement(JavaDriverDialect) } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

private object MongoDbElementPatterns {
    fun isConnected(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isFileConnected((element as? PsiElement)?.containingFile)

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isFileConnected((element as? PsiElement)?.containingFile)

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isFileConnected(psiFile: PsiFile?): Boolean {
                psiFile ?: return false
                psiFile.originalFile.virtualFile ?: return false

                val dbDataSource =
                    MongoDbVirtualFileDataSourceProvider().getDataSource(
                        psiFile.project,
                        psiFile.originalFile.virtualFile,
                    )

                return !(dbDataSource == null || dbDataSource.localDataSource?.isConnected() == false)
            }
        }

    fun AutocompletionEntry.toLookupElement(dialect: Dialect<PsiElement, Project>): LookupElement {
        val lookupElement =
            LookupElementBuilder
                .create(entry)
                .withInsertHandler { _, _ ->
                    val application = ApplicationManager.getApplication()
                    val probe = application.getService(AutocompleteSuggestionAcceptedProbe::class.java)

                    when (this.type) {
                        AutocompletionEntry.AutocompletionEntryType.DATABASE ->
                            probe.databaseCompletionAccepted(dialect)

                        AutocompletionEntry.AutocompletionEntryType.COLLECTION ->
                            probe.collectionCompletionAccepted(dialect)

                        AutocompletionEntry.AutocompletionEntryType.FIELD ->
                            probe.fieldCompletionAccepted(dialect)
                    }
                }
                .withIcon(
                    when (type) {
                        AutocompletionEntry.AutocompletionEntryType.DATABASE -> Icons.databaseAutocompleteEntry
                        AutocompletionEntry.AutocompletionEntryType.COLLECTION -> Icons.collectionAutocompleteEntry
                        AutocompletionEntry.AutocompletionEntryType.FIELD -> Icons.fieldAutocompleteEntry
                    },
                )
                .withTypeText(
                    if (type == AutocompletionEntry.AutocompletionEntryType.FIELD) {
                        dialect.formatter.formatType(bsonType!!)
                    } else {
                        type.presentableName
                    },
                    true,
                )
                .withCaseSensitivity(true)
                .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)

        return PrioritizedLookupElement.withPriority(lookupElement, Double.MAX_VALUE)
    }

    fun isDatabaseReference(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isDatabaseReference((element as? PsiElement))

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isDatabaseReference((element as? PsiElement))

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isDatabaseReference(element: PsiElement?): Boolean {
                element ?: return false
                val dialect = element.containingFile?.originalFile?.dialect ?: return false
                return dialect.parser.isReferenceToDatabase(element)
            }
        }

    fun isCollectionReference(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isCollectionReference((element as? PsiElement))

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isCollectionReference((element as? PsiElement))

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isCollectionReference(element: PsiElement?): Boolean {
                element ?: return false
                val dialect = element.containingFile?.originalFile?.dialect ?: return false
                return dialect.parser.isReferenceToCollection(element)
            }
        }

    fun canBeFieldName(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isFieldName((element as? PsiElement))

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isFieldName((element as? PsiElement))

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isFieldName(element: PsiElement?): Boolean {
                element ?: return false
                val dialect = element.containingFile?.originalFile?.dialect ?: return false
                return dialect.parser.isReferenceToField(element)
            }
        }

    fun guessDatabaseAndCollection(source: PsiElement): Pair<String?, String?> {
        val dialect = source.containingFile?.originalFile?.dialect ?: return null to null
        val queryRoot = runCatching { dialect.parser.attachment(source) }.getOrElse {
            source.parentOfType<PsiMethod>()?.let { dialect.parser.attachment(it) }
        }

        val parsedQuery = queryRoot?.let { dialect.parser.parse(it) }
        val collectionReference = parsedQuery?.component<HasCollectionReference>()
        val queryCollection = collectionReference?.let { extractCollection(it) }

        val database = extractDatabase(collectionReference)
            ?: extractFromFileMetadata(source)
            ?: extractFromDialectContext(dialect, source)

        return database to queryCollection
    }

    private fun extractCollection(reference: HasCollectionReference): String? =
        when (val innerRef = reference.reference) {
            is HasCollectionReference.Known -> innerRef.namespace.collection
            is HasCollectionReference.OnlyCollection -> innerRef.collection
            else -> null
        }

    private fun extractDatabase(reference: HasCollectionReference?): String? =
        when (val innerRef = reference?.reference) {
            is HasCollectionReference.Known -> innerRef.namespace.database
            else -> null
        }

    private fun extractFromFileMetadata(source: PsiElement): String? =
        source.containingFile.database

    private fun extractFromDialectContext(dialect: Dialect<PsiElement, Project>, source: PsiElement): String? =
        dialect.connectionContextExtractor?.gatherContext(source.project)?.database
}
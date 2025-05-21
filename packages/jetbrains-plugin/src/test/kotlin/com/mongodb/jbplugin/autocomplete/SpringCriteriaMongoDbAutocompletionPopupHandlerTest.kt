package com.mongodb.jbplugin.autocomplete

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.DefaultSetup
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.assertAutocompletes
import com.mongodb.jbplugin.fixtures.assertDoesNotAutocomplete
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDatabase
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.fixtures.whenListCollections
import com.mongodb.jbplugin.fixtures.whenQueryingCollectionSchema
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import kotlinx.coroutines.test.runTest

@IntegrationTest
class SpringCriteriaMongoDbAutocompletionPopupHandlerTest {
    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
@Document(<caret>)
record Entity() {}
        """,
    )
    fun `should autocomplete collections from the current connection`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListCollections("myCollection", "anotherCollection")

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myCollection", "anotherCollection")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching(where(<caret>
    }
        """,
    )
    fun `should autocomplete fields from the current namespace`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching(
            Query.query(
                where("released").is(true)
            ).with(
                Sort.by(<caret>
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in a chained sort`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(Aggregation.newAggregation(), <caret>
    }
        """,
    )
    fun `should autocomplete collections from the current connection in an aggregate call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListCollections("myCollection", "anotherCollection")

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myCollection", "anotherCollection")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregateStream(Aggregation.newAggregation(), <caret>
    }
        """,
    )
    fun `should autocomplete collections from the current connection in an aggregateStream call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenListCollections("myCollection", "anotherCollection")

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myCollection", "anotherCollection")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(where(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in a Criteria inside an aggregate call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregateStream(
            Aggregation.newAggregation(
                Aggregation.match(where(<caret>))
            ),
            Book.class,
            Book.class
        ).toList();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in a Criteria inside an aggregateStream call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in a Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(Fields.fields(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in fields helper passed to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project(Fields.from(Fields.field(<caret>)))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in field helper passed to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude(Fields.fields(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in fields helper passed to andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andInclude(Fields.from(Fields.field(<caret>)))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in field helper passed to andInclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.project().andExclude(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in andExclude call chained to Aggregation#project call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(Sort.Direction.ASC, <caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(<caret>)
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Sort#by of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(Sort.Direction.ASC, <caret>)
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Sort#by with direction of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(
                        Sort.Order.asc(<caret>)
                    )
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Order#asc of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(
                        Sort.Order.desc(<caret>)
                    )
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Order#desc of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(
                        Sort.Order.by(<caret>)
                    )
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Order#by of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.sort(
                    Sort.by(
                        List.of(
                            Sort.Order.by(<caret>)
                        )
                    )
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in List of Order of Aggregation#sort call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addFieldWithValueOf("addedField", <caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#addFields#addFieldsWithValueOf call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addFieldWithValueOf("addedField", Fields.field(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Fields#field call passed to Aggregation#addFields#addFieldsWithValueOf call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addField("addedField").withValueOf(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#addFields#withValueOf call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.addFields().addField("addedField").withValueOf(Fields.field(<caret>))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Fields#field call passed to Aggregation#addFields#withValueOf call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group("<caret>")
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group root call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group(Fields.fields("<caret>"))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group root call with Fields#fields`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group(Fields.fields(Fields.field("<caret>")))
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group root call with Fields#from(Fields#field)`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .sum(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#sum call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .avg(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#avg call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .first(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#first call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .last(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#last call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .max(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#max call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .min(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#min call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .push(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#push call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .addToSet(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should autocomplete fields from the current namespace in Aggregation#group#addToSet call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertAutocompletes("myField", "myField2")
        }
    }

    @ParsingTest(
        setup = DefaultSetup.SPRING_DATA,
        value = """
    public List<Book> allReleasedBooks() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.group()
                    .sum("asd")
                    .as(<caret>)
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
        """,
    )
    fun `should not autocomplete fields from the current namespace in Aggregation#group#as call`(
        fixture: CodeInsightTestFixture,
    ) = runTest {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase(dataSource, "myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        readModelProvider.whenQueryingCollectionSchema(
            "myDatabase.book",
            BsonObject(
                mapOf(
                    "myField" to BsonString,
                    "myField2" to BsonString,
                ),
            )
        )

        fixture.type('"')
        eventually {
            fixture.assertDoesNotAutocomplete("myField", "myField2")
        }
    }
}

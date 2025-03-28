package alt.mongodb.springcriteria;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching(
            Query.query(
                where("released").is(true)
            ).with(
                Sort.by("year", "title")
            )
        )
    }

    public List<Book> allReleasedBooksAgg() {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where("released").is(true)
                )
            ),
            Book.class,
            Book.class
        ).getMappedResults();
    }
}

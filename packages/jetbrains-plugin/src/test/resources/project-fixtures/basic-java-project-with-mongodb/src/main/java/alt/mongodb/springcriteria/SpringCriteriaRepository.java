package alt.mongodb.springcriteria;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Field;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

@Document("movies")
record Movie() {

}

public class SpringCriteriaRepository {
    private final MongoTemplate template;

    public SpringCriteriaRepository(MongoTemplate template) {
        this.template = template;
    }

    private List<Movie> allMoviesWithRatingAtLeast(int rating) {
        return template.find(
                query(where( "tomatoes.viewer.rating").gte(2456)),
                Movie.class
        );
    }

    private List<Movie> allMoviesWithRatingAtLeastAgg(int rating) {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(where( "tomatoes.viewer.rating").gte(rating)),
                Aggregation.project("fieldA").andInclude("fieldB").andExclude("fieldC"),
                Aggregation.sort(Sort.Direction.ASC, "asd"),
                Aggregation.sort(Sort.by("rated", "qwe")),
                Aggregation.sort(Sort.by(Sort.Direction.ASC, "rates")),
                Aggregation.sort(Sort.by(Sort.Order.by("rated"), Sort.Order.by("ratedd"))),
                Aggregation.sort(Sort.by(List.of(Sort.Order.by("rated"), Sort.Order.by("rateds")))),
                Aggregation.addFields().addFieldWithValueOf("addedField", "value").build(),
                Aggregation.addFields().addFieldWithValueOf("addedField", Fields.field("qwe")).build(),
                Aggregation.addFields().addField("addedField").withValueOf("rateds").build(),
                Aggregation.addFields().addField("addedField").withValueOf(Fields.field("asd")).build()
            ),
            Movie.class,
            Movie.class
        ).getMappedResults();
    }

    private void updateLanguageOfAllMoviesWithRatingAtLeast(int rating, String newLanguage) {
        template.updateFirst(query(where("tomatoes.viewer.rating").gte(rating)), update("language", newLanguage), Movie.class);
    }
}

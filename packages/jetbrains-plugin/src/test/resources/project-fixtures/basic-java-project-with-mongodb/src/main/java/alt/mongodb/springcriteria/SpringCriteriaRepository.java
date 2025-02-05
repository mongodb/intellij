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
                Aggregation.match(where( "tomatoes.viewer.rating").gte("S")),
                Aggregation.project("fieldA").andInclude("fieldB").andExclude("fieldC")
            ),
            Movie.class,
            Movie.class
        ).getMappedResults();
    }

    private void updateLanguageOfAllMoviesWithRatingAtLeast(int rating, String newLanguage) {
        template.updateFirst(query(where("tomatoes.viewer.rating").gte(rating)), update("language", newLanguage), Movie.class);
    }
}

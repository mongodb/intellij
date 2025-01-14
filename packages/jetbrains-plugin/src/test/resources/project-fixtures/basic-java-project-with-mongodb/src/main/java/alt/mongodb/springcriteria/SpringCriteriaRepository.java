package alt.mongodb.springcriteria;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;

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
                query(where( "tomatoes.viewer.rating").gte(rating)),
                Movie.class
        );
    }

    private List<Movie> allMoviesWithRatingAtLeastAgg(int rating) {
        return template.aggregate(
            Aggregation.newAggregation(
                Aggregation.match(
                    where( "tomatoes.viewer.rating").gte(rating)
                )
            ),
            Movie.class,
            Movie.class
        ).getMappedResults();
    }

    private void updateLanguageOfAllMoviesWithRatingAtLeast(int rating, String newLanguage) {
        template.updateMulti(query(where("tomatoes.viewer.rating").gte(rating)), update("key", "value"), Movie.class);
    }
}

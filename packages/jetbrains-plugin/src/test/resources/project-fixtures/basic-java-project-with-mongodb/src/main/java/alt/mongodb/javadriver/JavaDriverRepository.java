package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

enum Rate {
    UNRATED,
    PASSED,
    APPROVED,
    TV_14,
    TV_PG
}
enum Language {
    CATALAN,
    ENGLISH,
    FRENCH,
    GERMAN,
    HINDI,
    SPANISH,
}

public class JavaDriverRepository {
    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public Document notIndexedQuery(String db, Language lang) {
        return client
            .getDatabase(db)
            .getCollection("movies")
            .find(
                Filters.and(
                    Filters.gt("tomatoes.critic.numReviews", 1),
                    Filters.eq("rated", Rate.TV_PG),
                    Filters.eq("languages", lang),
                    Filters.gt("delivered", 1993)
                )
            ).sort(Sorts.ascending("year"))
            .first();
    }

    public Document notIndexedAnotherQuery(String db, Language lang) {
        return client
            .getDatabase(db)
            .getCollection("movies")
            .find(
                Filters.eq("rated", Rate.TV_PG)
            )
            .first();
    }
}

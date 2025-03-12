package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
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

    public Document notIndexedQuery(String lang) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .find(
                Filters.and(
                    Filters.gt("tomatoes.critic.numReviews", 1),
                    Filters.eq("rated", "r"),
                    Filters.eq("languages", 12),
                    Filters.gt("deliveredx", 1993)
                )
            ).sort(Sorts.ascending("year"))
            .first();
    }
//
//    public Document notIndexedAnotherQuery(Language lang) {
//        return client
//            .getDatabase("sample_mflix")
//            .getCollection("movies")
//            .find(
//                Filters.eq("rated", "abc")
//            )
//            .first();
//    }
}

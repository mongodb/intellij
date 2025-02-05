package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.*;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public Document notIndexedQuery(String string) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .find(Filters.eq("test", 123))
            .first();
    }
}

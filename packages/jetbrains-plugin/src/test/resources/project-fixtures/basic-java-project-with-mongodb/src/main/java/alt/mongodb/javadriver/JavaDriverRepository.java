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
import org.springframework.data.mongodb.core.aggregation.Fields;

import java.util.*;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public Document randomQuery(
        String string,
        Date date,
        int intVal,
        float floatVal,
        ObjectId objectId,
        UUID uuid,
        boolean bool,
        Object anyObj
    ) {
        return client
            .getDatabase(string)
            .getCollection(string)
            .find(Filters.and(
                Filters.eq(objectId),
                Filters.eq("string", string),
                Filters.eq("date", date),
                Filters.eq("int", intVal),
                Filters.eq("float", floatVal),
                Filters.eq("uuid", uuid),
                Filters.eq("bool", bool),
                Filters.eq("anyObj", anyObj)
            ))
            .first();
    }

    public void updateMoviesByYear(int year) {
        client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .updateMany(
                Filters.and(Filters.eq("year", year), Filters.ne("languages", "Esperanto")),
                Updates.combine(
                    Updates.inc(IMDB_VOTES, 1),
                    Updates.inc(AWARDS_WINS, 1),
                    Updates.set("other", 25),
                    Updates.pull(IMDB_VOTES, Filters.eq("a", 1)),
                    Updates.pullAll(IMDB_VOTES, List.of("1", "2")),
                    Updates.push("languages", "Esperanto")
                )
            );
    }

    public List<Document> findMoviesByYear(int year) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .find(Filters.eq("year", year))
//            .sort(Sorts.ascending(IMDB_VOTES))
            .sort(Sorts.orderBy(Sorts.ascending(IMDB_VOTES), Sorts.descending("_id")))
            .into(new ArrayList<>());
    }

    public Document queryMovieById(ObjectId id) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .aggregate(List.of(Aggregates.match(
                Filters.eq(id)
            ), Aggregates.project(Projections.include("title", "year"))))
            .first();
    }

    public List<Document> queryMoviesByYear(int year) {
        return client
            .getDatabase("sample_mflix")
            .getCollection("movies")
            .aggregate(
                List.of(
                    Aggregates.group(
                        null,
                        Accumulators.topN(
                            "3fields",
                            Sorts.descending("year"),
                            "fields",
                            3
                        )
                    )
                )
            )
            .into(new ArrayList<>());
    }
}

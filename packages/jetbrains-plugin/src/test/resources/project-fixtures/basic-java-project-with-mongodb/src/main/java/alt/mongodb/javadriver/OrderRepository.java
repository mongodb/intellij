package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository {
    private final MongoCollection<Document> orders;

    public OrderRepository(MongoClient client) {
        this.orders = client.getDatabase("intellij")
            .getCollection("orders");
    }

    public List<Document> allPendingArrivalOrders(boolean value) {
        return orders.find(
                Filters.and(
                    Filters.eq("shipping.shipped", true),
                    Filters.eq("arrival.arrived", false)
                )
            ).into(new ArrayList<>());
    }

    public List<Document> allPriorityPendingOrders() {
        var startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        return orders.find(
                Filters.and(
                    Filters.eq("shipping.shipped", true),
                    Filters.eq("arrival.arrived", false),
                    Filters.lte("shipping.date", startDate)
                )
            ).sort(Sorts.ascending("shipping.date"))
            .into(new ArrayList<>());
    }

    public Long countAllOrdersByArrivalStatus(boolean status) {
        return orders.aggregate(
            List.of(
                Aggregates.match(
                    Filters.and(
                        Filters.eq("shipping.shipped", true),
                        Filters.eq("arrival.arrived", status)
                    )
                ),
                Aggregates.count()
            )
        ).first().getLong("count");
    }
}

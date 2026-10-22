package no.uib.dat250.LMS.events;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "lms.messaging.enabled", havingValue = "true")
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitEventTransport transport;
    private final TransactionTemplate transactions;
    public OutboxPublisher(JdbcTemplate jdbc, RabbitEventTransport transport, org.springframework.transaction.PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transport = transport; this.transactions = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString = "${lms.messaging.publish-interval-ms:2000}",
            initialDelayString = "${lms.messaging.publish-interval-ms:2000}")
    public void publishPending() {
        transactions.executeWithoutResult(transaction -> {
            var pending = jdbc.query("""
                    SELECT id, routing_key, payload::text, attempts FROM event_outbox
                    WHERE published_at IS NULL AND available_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at, id LIMIT 20 FOR UPDATE SKIP LOCKED
                    """, (row, index) -> new Pending(row.getObject("id", UUID.class), row.getString("routing_key"), row.getString("payload"), row.getInt("attempts")));
            for (var event : pending) {
                try {
                    transport.send(event.id(), event.routingKey(), event.payload());
                    jdbc.update("UPDATE event_outbox SET published_at = CURRENT_TIMESTAMP, attempts = attempts + 1 WHERE id = ?", event.id());
                } catch (Exception failure) {
                    if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                    long delay = Math.min(60, 1L << Math.min(event.attempts() + 1, 6));
                    jdbc.update("UPDATE event_outbox SET attempts = attempts + 1, available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second') WHERE id = ?", delay, event.id());
                    LoggerFactory.getLogger(OutboxPublisher.class).warn("Event delivery is pending; the outbox will retry.");
                    break;
                }
            }
        });
    }
    private record Pending(UUID id, String routingKey, String payload, int attempts) {}
}

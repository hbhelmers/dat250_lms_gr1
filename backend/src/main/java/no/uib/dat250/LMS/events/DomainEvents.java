package no.uib.dat250.LMS.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DomainEvents {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public DomainEvents(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    // The user action and pending event commit together. RabbitMQ is contacted later.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(LearningEvent.Type type, Instant at, long courseId, long moduleId, long itemId,
            long studentId, long referenceId, int version, boolean late) {
        var event = new LearningEvent(1, UUID.randomUUID(), type, at, courseId, moduleId, itemId, studentId, referenceId, version, late);
        jdbc.update("""
                INSERT INTO event_outbox(id, item_id, student_id, routing_key, payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, event.id(), itemId, studentId, type.routingKey(), json.writeValueAsString(event));
    }
}

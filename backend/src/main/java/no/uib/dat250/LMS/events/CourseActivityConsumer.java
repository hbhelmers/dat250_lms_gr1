package no.uib.dat250.LMS.events;

import java.sql.Timestamp;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "lms.messaging.enabled", havingValue = "true")
public class CourseActivityConsumer {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public CourseActivityConsumer(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    @RabbitListener(queues = MessagingConfiguration.ACTIVITY_QUEUE)
    @Transactional
    public void receive(Message message) {
        LearningEvent event;
        try {
            event = json.readValue(message.getBody(), LearningEvent.class);
            if (event == null || !event.valid()) throw new IllegalArgumentException("Unsupported event envelope.");
        } catch (RuntimeException failure) {
            throw new AmqpRejectAndDontRequeueException("Invalid learning event; moved to dead letter queue.");
        }
        // A redelivery is harmless. Events about deleted content are acknowledged without recreating it.
        jdbc.update("""
                INSERT INTO course_activity(event_id, event_type, occurred_at, item_id, student_id, reference_id, version, late)
                SELECT ?, ?, ?, i.id, u.id, ?, ?, ? FROM content_items i
                JOIN course_modules m ON m.id = i.module_id JOIN users u ON u.id = ?
                WHERE i.id = ? AND m.id = ? AND m.course_id = ?
                  AND ((? = 'QUIZ_SUBMITTED' AND EXISTS (SELECT 1 FROM quiz_attempts q
                       WHERE q.id = ? AND q.quiz_id = i.id AND q.user_id = u.id))
                    OR (? IN ('ASSIGNMENT_SUBMITTED', 'GRADE_PUBLISHED') AND EXISTS (SELECT 1 FROM assignment_submissions s
                       WHERE s.id = ? AND s.assignment_id = i.id AND s.user_id = u.id)))
                ON CONFLICT (event_id) DO NOTHING
                """, event.id(), event.type().name(), Timestamp.from(event.occurredAt()), event.referenceId(), event.version(), event.late(),
                event.studentId(), event.itemId(), event.moduleId(), event.courseId(), event.type().name(), event.referenceId(),
                event.type().name(), event.referenceId());
    }
}

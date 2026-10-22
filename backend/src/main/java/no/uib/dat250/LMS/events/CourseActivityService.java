package no.uib.dat250.LMS.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import no.uib.dat250.LMS.course.CourseAccess;
import no.uib.dat250.LMS.course.CourseRole;

@Service @Transactional(readOnly = true)
public class CourseActivityService {
    private final CourseAccess access;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CourseActivityService(CourseAccess access, JdbcTemplate jdbc, Clock clock) { this.access = access; this.jdbc = jdbc; this.clock = clock; }
    public List<Activity> list(Long courseId, String email) {
        var member = access.requireMember(courseId, email);
        boolean teacher = member.getRole() == CourseRole.TEACHER;
        var now = Timestamp.from(clock.instant());
        return jdbc.query("""
                SELECT a.*, i.title, m.id AS module_id, u.name, u.email
                FROM course_activity a JOIN content_items i ON i.id = a.item_id
                JOIN course_modules m ON m.id = i.module_id JOIN users u ON u.id = a.student_id
                WHERE m.course_id = ? AND (? OR (a.student_id = ? AND i.published
                  AND (i.visible_from IS NULL OR i.visible_from <= ?)
                  AND (i.visible_until IS NULL OR i.visible_until > ?)))
                ORDER BY a.occurred_at DESC, a.event_id DESC LIMIT 50
                """, (row, index) -> new Activity(row.getObject("event_id", UUID.class), LearningEvent.Type.valueOf(row.getString("event_type")),
                row.getTimestamp("occurred_at").toInstant(), row.getLong("module_id"), row.getLong("item_id"), row.getString("title"),
                teacher ? row.getString("name") : null, teacher ? row.getString("email") : null, row.getInt("version"), row.getBoolean("late")),
                courseId, teacher, member.getUser().getId(), now, now);
    }
    public record Activity(UUID id, LearningEvent.Type type, Instant occurredAt, long moduleId, long itemId, String itemTitle,
            String studentName, String studentEmail, int version, boolean late) {}
}

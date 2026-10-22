package no.uib.dat250.LMS.events;

import java.time.Instant;
import java.util.UUID;

// Public event envelope v1 contains identifiers, never answers, grades, feedback or file contents.
public record LearningEvent(int schemaVersion, UUID id, Type type, Instant occurredAt, long courseId,
        long moduleId, long itemId, long studentId, long referenceId, int version, boolean late) {
    public enum Type {
        QUIZ_SUBMITTED("quiz.submitted"), ASSIGNMENT_SUBMITTED("assignment.submitted"), GRADE_PUBLISHED("grade.published");
        private final String routingKey;
        Type(String routingKey) { this.routingKey = routingKey; }
        public String routingKey() { return routingKey; }
    }
    public boolean valid() {
        return schemaVersion == 1 && id != null && type != null && occurredAt != null && courseId > 0
                && moduleId > 0 && itemId > 0 && studentId > 0 && referenceId > 0 && version > 0;
    }
}

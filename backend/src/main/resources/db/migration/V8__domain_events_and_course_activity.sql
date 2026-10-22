CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    item_id BIGINT NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    routing_key VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_outbox_pending_idx ON event_outbox(available_at, created_at) WHERE published_at IS NULL;

CREATE TABLE course_activity (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('QUIZ_SUBMITTED','ASSIGNMENT_SUBMITTED','GRADE_PUBLISHED')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    item_id BIGINT NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reference_id BIGINT NOT NULL CHECK (reference_id > 0),
    version INTEGER NOT NULL CHECK (version > 0),
    late BOOLEAN NOT NULL
);
CREATE INDEX course_activity_item_idx ON course_activity(item_id, occurred_at DESC);
CREATE INDEX course_activity_student_idx ON course_activity(student_id, occurred_at DESC);

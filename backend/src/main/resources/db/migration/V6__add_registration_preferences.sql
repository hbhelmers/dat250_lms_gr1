ALTER TABLE users ADD COLUMN preferred_role VARCHAR(16) NOT NULL DEFAULT 'STUDENT'
    CHECK (preferred_role IN ('STUDENT', 'TEACHER'));
UPDATE users u SET preferred_role = 'TEACHER'
    WHERE EXISTS (SELECT 1 FROM course_enrollments e WHERE e.user_id = u.id AND e.role = 'TEACHER');
ALTER TABLE courses ADD COLUMN example_template VARCHAR(32);

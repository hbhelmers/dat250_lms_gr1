-- Group work: one submission per group; every member sees it and its grade.
ALTER TABLE assignments ADD COLUMN group_work BOOLEAN NOT NULL DEFAULT FALSE;
-- Coding exercises: optional repository with starter code that students clone.
ALTER TABLE assignments ADD COLUMN starter_repo_url VARCHAR(500);

ALTER TABLE assignment_submissions ADD COLUMN group_id BIGINT REFERENCES course_groups(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX assignment_submissions_group_version ON assignment_submissions(assignment_id, group_id, version)
    WHERE group_id IS NOT NULL;

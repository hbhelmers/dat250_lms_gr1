ALTER TABLE courses ADD COLUMN institution VARCHAR(8) CHECK (institution IN ('HVL', 'UIB'));
ALTER TABLE courses ADD COLUMN course_code VARCHAR(20);
ALTER TABLE courses ADD COLUMN self_enrollment BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE courses ADD COLUMN official_url VARCHAR(512);
ALTER TABLE courses ADD COLUMN catalog_key VARCHAR(32) UNIQUE;
ALTER TABLE courses ADD CONSTRAINT catalog_institution_required CHECK (NOT self_enrollment OR institution IS NOT NULL);
CREATE INDEX courses_catalog_idx ON courses(institution, course_code) WHERE self_enrollment;

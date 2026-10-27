-- Additive schema, applied automatically ONLY to the isolated demo by local_demo.py.
CREATE TABLE IF NOT EXISTS student_assessment_plan (
    student_id BIGINT NOT NULL PRIMARY KEY,
    start_date DATE NOT NULL,
    deadline DATE NOT NULL,
    goals_json LONGTEXT NOT NULL,
    version INT NOT NULL,
    create_time DATETIME NOT NULL,
    edit_time DATETIME NOT NULL
);

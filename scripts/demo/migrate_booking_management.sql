-- Additive migration. local_demo.py applies this only to the isolated demo database.
ALTER TABLE assessment_booking ADD COLUMN management_key VARCHAR(64) DEFAULT NULL;
ALTER TABLE assessment_booking ADD COLUMN replacement_booking_id BIGINT DEFAULT NULL;
CREATE UNIQUE INDEX uk_booking_management ON assessment_booking (creator, management_key);

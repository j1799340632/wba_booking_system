-- Additive migration for the isolated demo only. Run once before starting the new backend.
-- No data is deleted. Do not apply automatically to the original deployment.
ALTER TABLE assessment_booking ADD COLUMN request_key VARCHAR(64) DEFAULT NULL;
ALTER TABLE assessment_booking ADD COLUMN request_hash CHAR(64) DEFAULT NULL;
CREATE UNIQUE INDEX uk_booking_request ON assessment_booking (creator, request_key);

-- Synthetic fixtures for compose.demo.yml ONLY; never run against an existing deployment.
-- The public demo password is WbaDemo!2026; all addresses use the reserved .test domain.
USE unsw_wba;
SET time_zone = 'Australia/Sydney';
SET @demo_monday = DATE_ADD(CURDATE(), INTERVAL (7 - WEEKDAY(CURDATE())) DAY);

CREATE TABLE demo_metadata (id INT PRIMARY KEY, next_monday DATE NOT NULL, seeded_at DATETIME NOT NULL);
INSERT INTO demo_metadata VALUES (1, @demo_monday, NOW());

INSERT INTO user (id, role_id, email, password, name, title, hospital, department, create_time) VALUES
  (900000001, 4, 'student@wba.test', '$2a$10$u4VyS0LsWgQ6lVpeh1gDou8G738CAD5CMUTs6o0t3geGoz.4w4tG2', 'Demo Student', 'Student', 'Prince of Wales Hospital', 'General Medicine', NOW()),
  (900000002, 3, 'chen@wba.test', '$2a$10$u4VyS0LsWgQ6lVpeh1gDou8G738CAD5CMUTs6o0t3geGoz.4w4tG2', 'Dr Chen', 'Dr', 'Prince of Wales Hospital', 'General Medicine', NOW()),
  (900000003, 3, 'lee@wba.test', '$2a$10$u4VyS0LsWgQ6lVpeh1gDou8G738CAD5CMUTs6o0t3geGoz.4w4tG2', 'Dr Lee', 'Dr', 'Prince of Wales Hospital', 'General Medicine', NOW());
INSERT INTO user_settings (id, user_id, is_receive_email, is_message_sound) VALUES
  (900000001, 900000001, 0, 0), (900000002, 900000002, 0, 0), (900000003, 900000003, 0, 0);

INSERT INTO assessment_slot
  (id, start_time, end_time, repeat_type, next_available_time, hospital, department, location, type, creator, create_time, is_enable)
VALUES
  (910000001, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '09:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '09:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '09:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000002, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room B', 'Mini-CEX', 900000003, NOW(), 1),
  (910000003, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '15:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '15:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '15:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000004, TIMESTAMP(@demo_monday + INTERVAL 3 DAY, '14:00:00'), TIMESTAMP(@demo_monday + INTERVAL 3 DAY, '14:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 3 DAY, '14:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000005, TIMESTAMP(@demo_monday + INTERVAL 2 DAY, '14:00:00'), TIMESTAMP(@demo_monday + INTERVAL 2 DAY, '14:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 2 DAY, '14:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000006, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '16:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '16:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '16:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000007, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '17:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '17:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '17:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 0),
  (910000008, TIMESTAMP(@demo_monday + INTERVAL 8 DAY, '14:00:00'), TIMESTAMP(@demo_monday + INTERVAL 8 DAY, '14:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 8 DAY, '14:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'Mini-CEX', 900000002, NOW(), 1),
  (910000009, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:00:00'), TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:30:00'), 0, TIMESTAMP(@demo_monday + INTERVAL 1 DAY, '14:00:00'), 'Prince of Wales Hospital', 'General Medicine', 'Demo Room A', 'DOP', 900000002, NOW(), 1);
INSERT INTO assessment_booking (id, creator, assessment_slot_id, start_time, end_time, status, create_time)
SELECT 920000001, 900000001, id, start_time, end_time, 0, NOW()
FROM assessment_slot WHERE id = 910000006;

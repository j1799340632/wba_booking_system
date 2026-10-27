-- Database users and passwords are configured through Docker Compose.
-- The Docker environment variables have already created the database and users for you; here's a further step to ensure that all IP addresses are allowed access and all permissions are granted
CREATE DATABASE IF NOT EXISTS `unsw_wba`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `unsw_wba`;

-- Database schema and non-personal reference data.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for assessment_booking
-- ----------------------------
DROP TABLE IF EXISTS `assessment_booking`;
CREATE TABLE `assessment_booking` (
                                      `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                                      `creator` bigint NOT NULL COMMENT 'The student''s id who is booked the slot',
                                      `assessment_slot_id` bigint NOT NULL COMMENT 'The slot id which has been booked',
                                      `start_time` datetime NOT NULL COMMENT 'Booking start time',
                                      `end_time` datetime NOT NULL COMMENT 'Booking end time',
                                      `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Booking status, 0: Pending, 1: Confirmed, 2: Completed, 3: Declined, 4 Canceled',
                                      `score` double DEFAULT NULL COMMENT 'When booking status is completed, the assess score',
                                      `feedback` text COLLATE utf8mb4_general_ci COMMENT 'When booking status is completed, the assess feedback',
                                      `completed_time` datetime DEFAULT NULL COMMENT 'Complete time',
                                      `create_time` datetime NOT NULL COMMENT 'Booking create time',
                                      `edit_time` datetime DEFAULT NULL COMMENT 'Booking edit time',
                                      `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted',
                                      `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
                                      `request_key` varchar(64) DEFAULT NULL,
                                      `request_hash` char(64) DEFAULT NULL,
                                      `management_key` varchar(64) DEFAULT NULL,
                                      `replacement_booking_id` bigint DEFAULT NULL,
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_booking_request` (`creator`,`request_key`),
                                      UNIQUE KEY `uk_booking_management` (`creator`,`management_key`),
                                      KEY `idx_slot_status_del` (`assessment_slot_id`,`status`,`is_delete`) USING BTREE,
                                      KEY `idx_creator_status_del` (`creator`,`status`,`is_delete`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for assessment_slot
-- ----------------------------
DROP TABLE IF EXISTS `assessment_slot`;
CREATE TABLE `assessment_slot` (
                                   `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                                   `start_time` datetime NOT NULL COMMENT 'Available start time',
                                   `end_time` datetime NOT NULL COMMENT 'Available end time',
                                   `repeat_type` tinyint(1) NOT NULL COMMENT 'Repeat type:0: no repeat, 1 daily, 2 weekly, 3 monthly, 4 yearly',
                                   `repeat_start_time` datetime DEFAULT NULL COMMENT 'Repeat start time',
                                   `repeat_end_time` datetime DEFAULT NULL COMMENT 'Repeat end time',
                                   `next_available_time` datetime NOT NULL COMMENT 'Most recent available time',
                                   `rrule` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'iCalendar(RFC 5545) rrule',
                                   `hospital` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Assess hostipal',
                                   `department` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Assess department',
                                   `location` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Assess location',
                                   `type` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'WBA types offered',
                                   `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Slot status, 0 available, 1 booked, 2 completed',
                                   `creator` bigint NOT NULL COMMENT 'Creator id',
                                   `create_time` datetime NOT NULL COMMENT 'Create time',
                                   `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                                   `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is available for student query',
                                   `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted',
                                   `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_creator_status_del` (`creator`,`status`,`is_enable`,`is_delete`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for department
-- ----------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department` (
                              `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                              `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Department name',
                              `creator` bigint DEFAULT NULL COMMENT 'Creator id',
                              `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is enabled?',
                              `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted?',
                              `create_time` datetime NOT NULL COMMENT 'Create time',
                              `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                              PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of department
-- ----------------------------
BEGIN;
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (1, 'A&E / Accident & Emergency (or Emergency Department)', 1, 1, 0, '2026-06-23 07:45:59', '2026-07-28 23:33:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (2, 'Acute Medical Unit (AMU)', 1, 1, 0, '2026-06-23 07:49:50', '2026-07-28 22:11:05');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (3, 'Administration', 1, 1, 0, '2026-06-23 07:50:24', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (4, 'Admissions', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (5, 'Allied Health', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (6, 'Anaesthetics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (7, 'Audiology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (8, 'Blood Bank', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (9, 'Burns Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (10, 'Cardiology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (11, 'Cardiothoracic Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (12, 'Care of the Elderly / Geriatrics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (13, 'Cath Lab / Catheterisation Laboratory', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (14, 'Central Sterile Services Department (CSSD)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (15, 'Chaplaincy / Pastoral Care', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (16, 'Clinical Immunology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (17, 'Clinical Trials', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (18, 'Colorectal Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (19, 'Day Surgery Unit (DSU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (20, 'Dermatology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (21, 'Diagnostic Imaging', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (22, 'Dietetics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (23, 'Dietetics & Nutrition', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (24, 'Discharge Lounge', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (25, 'Ear, Nose & Throat (ENT) / Otorhinolaryngology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (26, 'Emergency Department (ED)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (27, 'Endocrinology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (28, 'Endoscopy Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (29, 'Gastroenterology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (30, 'General Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (31, 'General Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (32, 'Geriatric Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (33, 'Gynaecology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (34, 'Haematology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (35, 'Haematology & Oncology Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (36, 'High Dependency Unit (HDU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (37, 'Hospital at Home / HITH (Hospital in the Home)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (38, 'Infection Control', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (39, 'Infectious Diseases', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (40, 'Information Technology (IT)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (41, 'Intensity Care Unit (ICU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (42, 'Internal Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (43, 'Laboratory / Pathology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (44, 'Linen Services', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (45, 'Maternity Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (46, 'Maxillofacial Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (47, 'Medical Imaging / Radiology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (48, 'Medical Records', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (49, 'Mental Health Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (50, 'Microbiology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (51, 'Mortuary', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (52, 'Neonatal Intensive Care Unit (NICU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (53, 'Nephrology / Renal Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (54, 'Neurology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (55, 'Neurosurgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (56, 'Nuclear Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (57, 'Nutrition & Dietetics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (58, 'Obstetrics & Gynaecology (OB/GYN)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (59, 'Occupational Health', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (60, 'Occupational Therapy (OT)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (61, 'Oncology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (62, 'Ophthalmology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (63, 'Oral and Maxillofacial Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (64, 'Orthopaedics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (65, 'Outpatient Department (OPD)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (66, 'Paediatric Intensive Care Unit (PICU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (67, 'Paediatrics', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (68, 'Pain Management', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (69, 'Palliative Care', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (70, 'Pathology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (71, 'Patient Accounts / Billing', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (72, 'Patient Advocacy / Patient Liaison', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (73, 'Patient Transport', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (74, 'Pharmacy', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (75, 'Phlebotomy', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (76, 'Physiotherapy', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (77, 'Plastic Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (78, 'Podiatry', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (79, 'Psychiatry', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (80, 'Public Health', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (81, 'Pulmonology / Respiratory Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (82, 'Quality & Risk Management', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (83, 'Radiation Oncology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (84, 'Radiology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (85, 'Rehabilitation Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (86, 'Renal Unit / Dialysis', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (87, 'Reproductive Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (88, 'Respiratory Medicine', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (89, 'Rheumatology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (90, 'Social Work', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (91, 'Speech Pathology / Speech Therapy', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (92, 'Spinal Injuries Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (93, 'Special Care Nursery (SCN)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (94, 'Stroke Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (95, 'Surgical Assessment Unit (SAU)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (96, 'Theatre / Operating Theatre (OT)', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (97, 'Toxicology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (98, 'Transplant Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (99, 'Trauma Center / Trauma Unit', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (100, 'Ultrasound', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (101, 'Urology', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (102, 'Vascular Surgery', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (103, 'Voluntary Services', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (104, 'Ward / Inpatient Ward', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `department` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (105, 'Wound Care', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
COMMIT;

-- ----------------------------
-- Table structure for google_calendar_connection
-- ----------------------------
DROP TABLE IF EXISTS `google_calendar_connection`;
CREATE TABLE `google_calendar_connection` (
                                              `id` bigint NOT NULL AUTO_INCREMENT,
                                              `user_id` bigint NOT NULL,
                                              `google_email` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
                                              `refresh_token` text COLLATE utf8mb4_general_ci NOT NULL,
                                              `access_token` text COLLATE utf8mb4_general_ci,
                                              `access_token_expiry` datetime DEFAULT NULL,
                                              `is_enable` tinyint(1) NOT NULL DEFAULT '1',
                                              `creator` bigint DEFAULT NULL,
                                              `updater` bigint DEFAULT NULL,
                                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              PRIMARY KEY (`id`),
                                              UNIQUE KEY `uk_google_calendar_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for hospital
-- ----------------------------
DROP TABLE IF EXISTS `hospital`;
CREATE TABLE `hospital` (
                            `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                            `name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Hospital name',
                            `creator` bigint DEFAULT NULL COMMENT 'Creator id',
                            `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is enabled?',
                            `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted?',
                            `create_time` datetime NOT NULL COMMENT 'Create time',
                            `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of hospital
-- ----------------------------
BEGIN;
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (1, 'Prince of Wales Hospital', 1, 1, 0, '2026-06-23 07:45:59', '2026-07-30 14:03:52');
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (2, 'Sydney Children\'s Hospital', 1, 1, 0, '2026-06-23 07:49:50', '2026-06-24 13:49:12');
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (3, 'The Royal Hospital for Women', 1, 1, 0, '2026-06-23 07:50:24', '2026-06-24 13:49:12');
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (4, 'St Vincent\'s Hospital', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (5, 'St George Hospital', 1, 1, 0, '2026-07-24 21:46:42', '2026-07-28 22:11:16');
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (6, 'Sutherland Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (7, 'Liverpool Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (8, 'Bankstown-Lidcombe Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (9, 'Campbelltown Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (10, 'Fairfield Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (11, 'Albury Wodonga Health', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (12, 'Wagga Wagga Base Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (13, 'Griffith Base Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (14, 'Port Macquarie Base Hospital', 1, 1, 0, '2026-07-24 21:46:42', NULL);
INSERT INTO `hospital` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (15, 'Coffs Harbour Health Campus', 1, 1, 0, '2026-07-24 21:46:42', NULL);
COMMIT;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
                           `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                           `content` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Msg content',
                           `receiver` bigint NOT NULL COMMENT 'Msg receiver id',
                           `creator` bigint NOT NULL DEFAULT '0' COMMENT 'Msg sender id',
                           `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is read by receiver',
                           `read_time` datetime DEFAULT NULL COMMENT 'Read time',
                           `is_delivery` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is delivered to receiver',
                           `delivery_time` datetime DEFAULT NULL COMMENT 'Delivery time',
                           `create_time` datetime NOT NULL COMMENT 'Create time(Send time)',
                           `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                           `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted',
                           PRIMARY KEY (`id`),
                           KEY `idx_receiver_delivery` (`receiver`,`is_delivery`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
                        `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                        `role_id` bigint NOT NULL COMMENT 'Role id',
                        `email` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'User email(Login)',
                        `password` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Login password',
                        `name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'User real name',
                        `nickname` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'User nickname',
                        `title` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Like position(Dr. Mr.)',
                        `avatar` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'User avatar link',
                        `gender` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'User gender',
                        `age` int DEFAULT NULL COMMENT 'User age',
                        `z_id` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'UNSW zid',
                        `faculty` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'UNSW faculty',
                        `grade` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'UNSW grade',
                        `hospital` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Assess hostipal',
                        `department` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Assess department',
                        `location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Assess location',
                        `preferred_student_level` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Preferred student level',
                        `preferred_assess_type` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Preferred assess type',
                        `is_available` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is available to receive booking',
                        `available_days` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Available days',
                        `available_start_time` time DEFAULT NULL COMMENT 'Available start time',
                        `available_end_time` time DEFAULT NULL COMMENT 'Available end time',
                        `is_canceled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Account is canceled?',
                        `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Account is enabled?',
                        `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Account is deleted?',
                        `last_login_time` datetime DEFAULT NULL COMMENT 'Last login time',
                        `creator` bigint DEFAULT NULL COMMENT 'Creator id, if null, it indicates the user is registered by the user self',
                        `create_time` datetime NOT NULL COMMENT 'Registered time',
                        `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_email_not_deleted` (((case when (`is_delete` = 0) then `email` end))) USING BTREE,
                        KEY `idx_role_id_del` (`role_id`,`is_delete`,`is_enable`,`is_canceled`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of user
-- ----------------------------
BEGIN;

-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.


-- Original user sample rows omitted from the repository.

COMMIT;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
                             `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                             `permission_priority` int NOT NULL DEFAULT '4' COMMENT 'Role permission priority, the lower, the higher the permission',
                             `role_key` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Role key',
                             `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Role name',
                             `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Role is enabled?',
                             `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Role is deleted?',
                             `creator` bigint DEFAULT NULL COMMENT 'Creator id',
                             `create_time` datetime NOT NULL COMMENT 'Create time',
                             `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                             PRIMARY KEY (`id`),
                             KEY `idx_key_del` (`role_key`,`is_enable`,`is_delete`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of user_role
-- ----------------------------
BEGIN;
INSERT INTO `user_role` (`id`, `permission_priority`, `role_key`, `role_name`, `is_enable`, `is_delete`, `creator`, `create_time`, `edit_time`) VALUES (1, 1, 'SuAdmin', 'Super Admin', 1, 0, 1, '2026-07-29 16:36:05', NULL);
INSERT INTO `user_role` (`id`, `permission_priority`, `role_key`, `role_name`, `is_enable`, `is_delete`, `creator`, `create_time`, `edit_time`) VALUES (2, 2, 'Admin', 'Registrar', 1, 0, 1, '2026-06-23 07:45:59', '2026-06-24 13:49:12');
INSERT INTO `user_role` (`id`, `permission_priority`, `role_key`, `role_name`, `is_enable`, `is_delete`, `creator`, `create_time`, `edit_time`) VALUES (3, 3, 'PS', 'Placement Supervisor', 1, 0, 1, '2026-06-23 07:49:50', '2026-06-24 13:49:12');
INSERT INTO `user_role` (`id`, `permission_priority`, `role_key`, `role_name`, `is_enable`, `is_delete`, `creator`, `create_time`, `edit_time`) VALUES (4, 4, 'Stu', 'Student', 1, 0, 1, '2026-06-23 07:50:24', '2026-06-24 13:49:12');
COMMIT;

-- ----------------------------
-- Table structure for user_settings
-- ----------------------------
DROP TABLE IF EXISTS `user_settings`;
CREATE TABLE `user_settings` (
                                 `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                                 `user_id` bigint NOT NULL COMMENT 'User id',
                                 `is_receive_email` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is receive email notification?',
                                 `is_message_sound` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is play notification sound?',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of user_settings
-- ----------------------------
BEGIN;

-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.


-- Original user_settings sample rows omitted from the repository.

COMMIT;

-- ----------------------------
-- Table structure for wba_type
-- ----------------------------
DROP TABLE IF EXISTS `wba_type`;
CREATE TABLE `wba_type` (
                            `id` bigint NOT NULL COMMENT 'Snowflake primary key',
                            `name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Wba type name',
                            `creator` bigint DEFAULT NULL COMMENT 'Creator id',
                            `is_enable` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Is enabled?',
                            `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Is deleted?',
                            `create_time` datetime NOT NULL COMMENT 'Create time',
                            `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of wba_type
-- ----------------------------
BEGIN;
INSERT INTO `wba_type` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (1, 'Mini-CEX', 1, 1, 0, '2026-06-23 07:45:59', '2026-07-29 10:32:53');
INSERT INTO `wba_type` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (2, 'DOP', 1, 1, 0, '2026-06-23 07:49:50', '2026-06-24 13:49:12');
INSERT INTO `wba_type` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (3, 'X-Ray', 1, 1, 0, '2026-06-23 07:50:24', '2026-06-24 13:49:12');
INSERT INTO `wba_type` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (4, 'CEX', 1, 1, 0, '2026-06-24 03:56:51', '2026-06-24 13:49:12');
INSERT INTO `wba_type` (`id`, `name`, `creator`, `is_enable`, `is_delete`, `create_time`, `edit_time`) VALUES (5, 'Surgery', 1, 1, 0, '2026-07-24 22:19:54', '2026-07-24 22:20:02');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

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

-- Minimal assistant operation observations (no conversation or assessment content).
CREATE TABLE IF NOT EXISTS assistant_event (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    action VARCHAR(32) DEFAULT NULL,
    elapsed_ms BIGINT NOT NULL,
    alternative_groups INT NOT NULL DEFAULT 0,
    model_calls INT NOT NULL DEFAULT 0,
    model_name VARCHAR(80) DEFAULT NULL,
    usage_known BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_tokens BIGINT DEFAULT NULL,
    completion_tokens BIGINT DEFAULT NULL,
    total_tokens BIGINT DEFAULT NULL,
    KEY idx_assistant_created (created_at,stage,outcome),
    KEY idx_assistant_actor (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

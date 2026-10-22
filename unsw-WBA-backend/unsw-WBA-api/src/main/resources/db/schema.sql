/*
 Navicat Premium Dump SQL

 Source Server         : AzureUNSW
 Source Server Type    : MariaDB
 Source Server Version : 110806 (11.8.6-MariaDB-0+deb13u1 from Debian)
 Source Host           : 20.11.41.209:3306
 Source Schema         : unsw_wba

 Target Server Type    : MariaDB
 Target Server Version : 110806 (11.8.6-MariaDB-0+deb13u1 from Debian)
 File Encoding         : 65001

 Date: 08/07/2026 22:38:27
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for assessment_booking
-- ----------------------------
CREATE TABLE IF NOT EXISTS `assessment_booking` (
  `id` bigint(19) NOT NULL COMMENT 'Snowflake primary key',
  `creator` bigint(19) NOT NULL COMMENT 'The student''s id who is booked the slot',
  `assessment_slot_id` bigint(19) NOT NULL COMMENT 'The slot id which has been booked',
  `start_time` datetime NOT NULL COMMENT 'Booking start time',
  `end_time` datetime NOT NULL COMMENT 'Booking end time',
  `type` varchar(255) DEFAULT NULL COMMENT 'WBA types offered',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Booking status, 0: Pending, 1: Confirmed, 2: Completed, 3: Declined',
  `score` double DEFAULT NULL COMMENT 'When booking status is completed, the assess score',
  `feedback` text DEFAULT NULL COMMENT 'When booking status is completed, the assess feedback',
  `completed_time` datetime DEFAULT NULL COMMENT 'Complete time',
  `create_time` datetime NOT NULL COMMENT 'Booking create time',
  `edit_time` datetime DEFAULT NULL COMMENT 'Booking edit time',
  `is_delete` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Is deleted',
  PRIMARY KEY (`id`),
  KEY `idx_slot_id` (`assessment_slot_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for assessment_slot
-- ----------------------------
CREATE TABLE IF NOT EXISTS `assessment_slot` (
  `id` bigint(19) NOT NULL COMMENT 'Snowflake primary key',
  `start_time` datetime NOT NULL COMMENT 'Available start time',
  `end_time` datetime NOT NULL COMMENT 'Available end time',
  `hospital` varchar(255) NOT NULL COMMENT 'Assess hostipal',
  `department` varchar(255) NOT NULL COMMENT 'Assess department',
  `location` varchar(255) NOT NULL COMMENT 'Assess location',
  `type` varchar(255) DEFAULT NULL COMMENT 'WBA types offered',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Slot status, 0 available, 1 has been booked',
  `creator` bigint(19) NOT NULL COMMENT 'Creator id',
  `create_time` datetime NOT NULL COMMENT 'Create time',
  `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
  `is_delete` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Is deleted',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for user
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint(19) NOT NULL COMMENT 'Snowflake primary key',
  `role_id` bigint(19) NOT NULL COMMENT 'Role id',
  `name` varchar(50) DEFAULT NULL COMMENT 'User real name',
  `nickname` varchar(50) DEFAULT NULL COMMENT 'User nickname',
  `title` varchar(50) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL COMMENT 'User avatar link',
  `gender` varchar(20) DEFAULT NULL COMMENT 'User gender',
  `age` int(3) DEFAULT NULL COMMENT 'User age',
  `hospital` varchar(255) DEFAULT NULL COMMENT 'Assess hostipal',
  `department` varchar(255) DEFAULT NULL COMMENT 'Assess department',
  `location` varchar(255) DEFAULT NULL COMMENT 'Assess location',
  `preferred_student_level` varchar(255) DEFAULT NULL COMMENT 'Preferred student level',
  `preferred_assess_type` varchar(255) DEFAULT NULL COMMENT 'Preferred assess type',
  `available_days` varchar(255) DEFAULT NULL COMMENT 'Available days',
  `available_start_time` time DEFAULT NULL COMMENT 'Available start time',
  `available_end_time` time DEFAULT NULL COMMENT 'Available end time',
  `email` varchar(255) NOT NULL COMMENT 'User email(Login)',
  `password` varchar(255) NOT NULL COMMENT 'Login password',
  `is_canceled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Account is canceled?',
  `is_enable` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Account is enabled?',
  `is_delete` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Account is deleted?',
  `last_login_time` datetime DEFAULT NULL COMMENT 'Last login time',
  `create_time` datetime NOT NULL COMMENT 'Registered time',
  `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint(19) NOT NULL COMMENT 'Snowflake primary key',
  `role_key` varchar(50) NOT NULL COMMENT 'Role key',
  `role_name` varchar(50) DEFAULT NULL COMMENT 'Role name',
  `is_enable` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Role is enabled?',
  `is_delete` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Role is deleted?',
  `creator` bigint(19) DEFAULT NULL COMMENT 'Creator id',
  `create_time` datetime NOT NULL COMMENT 'Create time',
  `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;

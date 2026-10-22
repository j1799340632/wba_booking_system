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

 Date: 08/07/2026 22:38:14
*/

-- ----------------------------
-- Records of assessment_booking (只有表为空时才插入)
-- ----------------------------

-- Original assessment_booking sample rows omitted from the repository.



-- ----------------------------
-- Records of assessment_slot (只有表为空时才插入)
-- ----------------------------

-- Original assessment_slot sample rows omitted from the repository.



-- ----------------------------
-- Records of user (只有表为空时才插入)
-- ----------------------------

-- Original user sample rows omitted from the repository.



-- ----------------------------
-- Records of user_role (只有表为空时才插入)
-- ----------------------------
INSERT INTO `user_role` (`id`, `role_key`, `role_name`, `is_enable`, `is_delete`, `creator`, `create_time`, `edit_time`)
SELECT * FROM (
  SELECT 1, 'Admin', 'Registrar', 1, 0, 2069335646085259266, '2026-06-23 07:45:59', '2026-06-24 13:49:12' UNION ALL
  SELECT 2, 'PS', 'Placement Supervisor', 1, 0, 2069335646085259266, '2026-06-23 07:49:50', '2026-06-24 13:49:12' UNION ALL
  SELECT 3, 'Stu', 'Student', 1, 0, 2069335646085259266, '2026-06-23 07:50:24', '2026-06-24 13:49:12' UNION ALL
  SELECT 4, 'PS', 'Sub Supervisor', 1, 0, 2069335646085259266, '2026-06-24 03:56:51', '2026-06-24 13:49:12'
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `user_role` LIMIT 1);

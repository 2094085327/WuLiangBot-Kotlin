/*
 Navicat Premium Data Transfer

 Source Server         : wuliang
 Source Server Type    : MySQL
 Source Server Version : 80017
 Source Host           : localhost:3306
 Source Schema         : wuliang

 Target Server Type    : MySQL
 Target Server Version : 80017
 File Encoding         : 65001

 Date: 08/04/2025 21:55:44
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for directives_category
-- ----------------------------
DROP TABLE IF EXISTS `directives_category`;
CREATE TABLE `directives_category`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指令类别',
  `category_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指令描述',
  `del_status` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除  0为否 1为是',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of directives_category
-- ----------------------------
INSERT INTO `directives_category` VALUES (1, 'other', '其他指令', 0, '2025-04-04 14:21:02', NULL);
INSERT INTO `directives_category` VALUES (2, 'warframe', 'Warframe 相关指令', 0, '2025-04-04 14:21:02', NULL);
INSERT INTO `directives_category` VALUES (3, 'lifeRestart', '人生重开小游戏指令', 0, '2025-04-04 14:21:02', NULL);
INSERT INTO `directives_category` VALUES (4, 'genshin', '原神相关指令（已停止维护）', 0, '2025-04-04 14:21:02', NULL);

SET FOREIGN_KEY_CHECKS = 1;

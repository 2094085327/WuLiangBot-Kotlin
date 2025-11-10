/*
 Navicat Premium Data Transfer

 Source Server         : 122.51.5.228
 Source Server Type    : MariaDB
 Source Server Version : 101108 (10.11.8-MariaDB-0ubuntu0.24.04.1)
 Source Host           : 122.51.5.228:3306
 Source Schema         : wuliang

 Target Server Type    : MariaDB
 Target Server Version : 101108 (10.11.8-MariaDB-0ubuntu0.24.04.1)
 File Encoding         : 65001

 Date: 06/11/2025 09:13:06
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wf_other_name
-- ----------------------------
DROP TABLE IF EXISTS `wf_other_name`;
CREATE TABLE `wf_other_name`  (
  `id` int(30) NOT NULL AUTO_INCREMENT,
  `en_item_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `other_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `en_other`(`en_item_name` ASC, `other_name` ASC) USING BTREE COMMENT '别名唯一'
) ENGINE = InnoDB AUTO_INCREMENT = 177 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of wf_other_name
-- ----------------------------
INSERT INTO `wf_other_name` VALUES (1, 'Ash', '轮椅');
INSERT INTO `wf_other_name` VALUES (130, 'Ash', '轮椅甲');
INSERT INTO `wf_other_name` VALUES (2, 'Ash', '阿屎');
INSERT INTO `wf_other_name` VALUES (3, 'Atlas', '土甲');
INSERT INTO `wf_other_name` VALUES (172, 'Atlas', '石甲');
INSERT INTO `wf_other_name` VALUES (4, 'Banshee', '音妈');
INSERT INTO `wf_other_name` VALUES (168, 'Baruuk', '拳皇');
INSERT INTO `wf_other_name` VALUES (5, 'Baruuk', '武僧');
INSERT INTO `wf_other_name` VALUES (6, 'Blast', '爆炸');
INSERT INTO `wf_other_name` VALUES (7, 'Caliban', '卡利班');
INSERT INTO `wf_other_name` VALUES (8, 'Chroma', '龙');
INSERT INTO `wf_other_name` VALUES (9, 'Chroma', '龙甲');
INSERT INTO `wf_other_name` VALUES (167, 'Citrine', '宝石甲');
INSERT INTO `wf_other_name` VALUES (10, 'Citrine', '水晶');
INSERT INTO `wf_other_name` VALUES (11, 'Citrine', '水晶洞');
INSERT INTO `wf_other_name` VALUES (12, 'Citrine', '水晶甲');
INSERT INTO `wf_other_name` VALUES (13, 'Cold', '冰');
INSERT INTO `wf_other_name` VALUES (14, 'Cold', '冰冻');
INSERT INTO `wf_other_name` VALUES (15, 'Corrosive', '腐蚀');
INSERT INTO `wf_other_name` VALUES (161, 'Cyte-09', '09');
INSERT INTO `wf_other_name` VALUES (159, 'Cyte-09', '牢9');
INSERT INTO `wf_other_name` VALUES (165, 'Cyte-09', '牢九');
INSERT INTO `wf_other_name` VALUES (160, 'Cyte-09', '盖世太保');
INSERT INTO `wf_other_name` VALUES (162, 'Cyte-09', '老久');
INSERT INTO `wf_other_name` VALUES (163, 'Cyte-09', '老九');
INSERT INTO `wf_other_name` VALUES (164, 'Cyte-09', '零九');
INSERT INTO `wf_other_name` VALUES (16, 'Dagath', '赛马娘');
INSERT INTO `wf_other_name` VALUES (17, 'Dagath', '马娘');
INSERT INTO `wf_other_name` VALUES (18, 'Dante', '但丁');
INSERT INTO `wf_other_name` VALUES (19, 'Electricity', '电');
INSERT INTO `wf_other_name` VALUES (20, 'Electricity', '电击');
INSERT INTO `wf_other_name` VALUES (21, 'Ember', '火鸡');
INSERT INTO `wf_other_name` VALUES (22, 'Equinox', 'futa');
INSERT INTO `wf_other_name` VALUES (23, 'Equinox', '双子');
INSERT INTO `wf_other_name` VALUES (24, 'Equinox', '扶他');
INSERT INTO `wf_other_name` VALUES (25, 'Equinox', '扶她');
INSERT INTO `wf_other_name` VALUES (26, 'Equinox', '扶它');
INSERT INTO `wf_other_name` VALUES (27, 'Equinox', '阴阳');
INSERT INTO `wf_other_name` VALUES (28, 'Excalibur', '咖喱');
INSERT INTO `wf_other_name` VALUES (158, 'Frost', '冰甲');
INSERT INTO `wf_other_name` VALUES (29, 'Frost', '冰男');
INSERT INTO `wf_other_name` VALUES (30, 'Frost', '冰队');
INSERT INTO `wf_other_name` VALUES (31, 'Gara', '玻璃');
INSERT INTO `wf_other_name` VALUES (32, 'Gara', '琉璃');
INSERT INTO `wf_other_name` VALUES (33, 'Gara', '琉璃女');
INSERT INTO `wf_other_name` VALUES (34, 'Garuda', '血妈');
INSERT INTO `wf_other_name` VALUES (35, 'Gas', '毒气');
INSERT INTO `wf_other_name` VALUES (36, 'Gauss', '高斯');
INSERT INTO `wf_other_name` VALUES (37, 'Grende', '肥宅');
INSERT INTO `wf_other_name` VALUES (38, 'Gyre', '电妹');
INSERT INTO `wf_other_name` VALUES (39, 'Harrow', '主教');
INSERT INTO `wf_other_name` VALUES (40, 'Heat', '火');
INSERT INTO `wf_other_name` VALUES (41, 'Heat', '火焰');
INSERT INTO `wf_other_name` VALUES (42, 'Hildryn', '母牛');
INSERT INTO `wf_other_name` VALUES (43, 'Hydroid', '水男');
INSERT INTO `wf_other_name` VALUES (44, 'Impact', '冲击');
INSERT INTO `wf_other_name` VALUES (45, 'Inaros', '沙');
INSERT INTO `wf_other_name` VALUES (46, 'Inaros', '沙甲');
INSERT INTO `wf_other_name` VALUES (47, 'Inaros', '老沙');
INSERT INTO `wf_other_name` VALUES (48, 'Ivara', '弓');
INSERT INTO `wf_other_name` VALUES (49, 'Ivara', '弓妹');
INSERT INTO `wf_other_name` VALUES (50, 'Jade', '玉');
INSERT INTO `wf_other_name` VALUES (51, 'Jade', '玉甲');
INSERT INTO `wf_other_name` VALUES (52, 'Khora', '猫');
INSERT INTO `wf_other_name` VALUES (53, 'Khora', '猫甲');
INSERT INTO `wf_other_name` VALUES (126, 'Koumei', '口妹');
INSERT INTO `wf_other_name` VALUES (123, 'Koumei', '小梅');
INSERT INTO `wf_other_name` VALUES (129, 'Koumei', '扣妹');
INSERT INTO `wf_other_name` VALUES (124, 'Koumei', '赌博甲');
INSERT INTO `wf_other_name` VALUES (125, 'Koumei', '赌甲');
INSERT INTO `wf_other_name` VALUES (128, 'Koumei', '骰子妹');
INSERT INTO `wf_other_name` VALUES (54, 'Kullervo', '刀哥');
INSERT INTO `wf_other_name` VALUES (127, 'Kullervo', '刀甲');
INSERT INTO `wf_other_name` VALUES (171, 'Lavos', '炼金');
INSERT INTO `wf_other_name` VALUES (55, 'Lavos', '船长');
INSERT INTO `wf_other_name` VALUES (169, 'Lavos', '蛇甲');
INSERT INTO `wf_other_name` VALUES (56, 'Limbo', '小明');
INSERT INTO `wf_other_name` VALUES (57, 'Limbo', '李明博');
INSERT INTO `wf_other_name` VALUES (58, 'Loki', '洛基');
INSERT INTO `wf_other_name` VALUES (59, 'Mag', '磁妹');
INSERT INTO `wf_other_name` VALUES (60, 'Mag', '磁甲');
INSERT INTO `wf_other_name` VALUES (61, 'Magnetic', '磁力');
INSERT INTO `wf_other_name` VALUES (62, 'Mesa', '女枪');
INSERT INTO `wf_other_name` VALUES (63, 'Mesa', '弥撒');
INSERT INTO `wf_other_name` VALUES (64, 'Mirage', '小丑');
INSERT INTO `wf_other_name` VALUES (65, 'Nekros', '摸');
INSERT INTO `wf_other_name` VALUES (66, 'Nekros', '摸尸');
INSERT INTO `wf_other_name` VALUES (67, 'Nezha', '哪吒');
INSERT INTO `wf_other_name` VALUES (68, 'Nidus', '蛆');
INSERT INTO `wf_other_name` VALUES (69, 'Nidus', '蛆甲');
INSERT INTO `wf_other_name` VALUES (70, 'Nova', '减速娃');
INSERT INTO `wf_other_name` VALUES (71, 'Nova', '加速娃');
INSERT INTO `wf_other_name` VALUES (72, 'Nyx', '脑溢血');
INSERT INTO `wf_other_name` VALUES (73, 'Oberon', '奥伯龙');
INSERT INTO `wf_other_name` VALUES (74, 'Oberon', '奶爸');
INSERT INTO `wf_other_name` VALUES (75, 'Oberon', '驴');
INSERT INTO `wf_other_name` VALUES (76, 'Oberon', '驴王');
INSERT INTO `wf_other_name` VALUES (77, 'Oberon', '龙王');
INSERT INTO `wf_other_name` VALUES (78, 'Octavia', 'DJ');
INSERT INTO `wf_other_name` VALUES (174, 'Oraxia', '蜘蛛');
INSERT INTO `wf_other_name` VALUES (176, 'Oraxia', '蜘蛛妹');
INSERT INTO `wf_other_name` VALUES (175, 'Oraxia', '蜘蛛甲');
INSERT INTO `wf_other_name` VALUES (79, 'Protea', '普洱茶');
INSERT INTO `wf_other_name` VALUES (80, 'Protea', '茶');
INSERT INTO `wf_other_name` VALUES (81, 'Protea', '茶妹');
INSERT INTO `wf_other_name` VALUES (82, 'Puncture', '穿刺');
INSERT INTO `wf_other_name` VALUES (83, 'Qorvex', '反应堆');
INSERT INTO `wf_other_name` VALUES (84, 'Qorvex', '暖气片');
INSERT INTO `wf_other_name` VALUES (85, 'Radiation', '辐射');
INSERT INTO `wf_other_name` VALUES (86, 'Revenant', '夜灵');
INSERT INTO `wf_other_name` VALUES (87, 'Revenant', '夜灵甲');
INSERT INTO `wf_other_name` VALUES (88, 'Rhino', '牛');
INSERT INTO `wf_other_name` VALUES (89, 'Rhino', '牛甲');
INSERT INTO `wf_other_name` VALUES (90, 'Saryn', '毒妈');
INSERT INTO `wf_other_name` VALUES (91, 'Sevagoth', '幽灵');
INSERT INTO `wf_other_name` VALUES (92, 'Sevagoth', '幽灵甲');
INSERT INTO `wf_other_name` VALUES (93, 'Sevagoth', '鬼');
INSERT INTO `wf_other_name` VALUES (94, 'Sevagoth', '鬼甲');
INSERT INTO `wf_other_name` VALUES (95, 'Slash', '切割');
INSERT INTO `wf_other_name` VALUES (96, 'Styanax', '潘森');
INSERT INTO `wf_other_name` VALUES (173, 'Temple', '吉他');
INSERT INTO `wf_other_name` VALUES (97, 'Titania', '蝶');
INSERT INTO `wf_other_name` VALUES (98, 'Titania', '蝶妹');
INSERT INTO `wf_other_name` VALUES (99, 'Toxin', '毒');
INSERT INTO `wf_other_name` VALUES (100, 'Toxin', '毒素');
INSERT INTO `wf_other_name` VALUES (101, 'Trinity', '奶');
INSERT INTO `wf_other_name` VALUES (102, 'Trinity', '奶妈');
INSERT INTO `wf_other_name` VALUES (103, 'Valkyr', '瓦喵');
INSERT INTO `wf_other_name` VALUES (104, 'Vauban', '工程');
INSERT INTO `wf_other_name` VALUES (105, 'Viral', '病毒');
INSERT INTO `wf_other_name` VALUES (106, 'Void', '虚空');
INSERT INTO `wf_other_name` VALUES (107, 'Volt', '电男');
INSERT INTO `wf_other_name` VALUES (108, 'Voruna', '狼妹');
INSERT INTO `wf_other_name` VALUES (109, 'Voruna', '狼甲');
INSERT INTO `wf_other_name` VALUES (110, 'Wisp', '花');
INSERT INTO `wf_other_name` VALUES (166, 'Wisp', '花妈');
INSERT INTO `wf_other_name` VALUES (111, 'Wisp', '花甲');
INSERT INTO `wf_other_name` VALUES (112, 'Wukong', '悟空');
INSERT INTO `wf_other_name` VALUES (113, 'Wukong', '猴');
INSERT INTO `wf_other_name` VALUES (114, 'Wukong', '猴子');
INSERT INTO `wf_other_name` VALUES (115, 'Wukong', '猴甲');
INSERT INTO `wf_other_name` VALUES (116, 'Wukong', '猿');
INSERT INTO `wf_other_name` VALUES (150, 'Xaku', '虚空甲');
INSERT INTO `wf_other_name` VALUES (148, 'Xaku', '骨头');
INSERT INTO `wf_other_name` VALUES (117, 'Xaku', '骨架');
INSERT INTO `wf_other_name` VALUES (149, 'Xaku', '骨王');
INSERT INTO `wf_other_name` VALUES (118, 'Xaku', '骨甲');
INSERT INTO `wf_other_name` VALUES (119, 'Yareli', '水妹');
INSERT INTO `wf_other_name` VALUES (120, 'Yareli', '鸭梨');
INSERT INTO `wf_other_name` VALUES (121, 'Zephyr', '鸟姐');
INSERT INTO `wf_other_name` VALUES (122, 'Zephyr', '鸟甲');

SET FOREIGN_KEY_CHECKS = 1;

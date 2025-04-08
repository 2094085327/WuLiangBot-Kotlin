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

 Date: 08/04/2025 21:57:12
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for bot_directives
-- ----------------------------
DROP TABLE IF EXISTS `bot_directives`;
CREATE TABLE `bot_directives`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint(20) NULL DEFAULT NULL COMMENT '分类Id',
  `directive_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指令名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示描述',
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '具体描述',
  `regex` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指令校验正则',
  `enable` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用  0为否 1为是',
  `del_status` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除  0为否 1为是',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of bot_directives
-- ----------------------------
INSERT INTO `bot_directives` VALUES (2, 1, '帮助/菜单/help', '帮助信息', '获取帮助信息', '\\b(帮助|菜单|help)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (3, 1, '天气 城市', '查询城市的天气', '如使用「天气 北京」来查询北京当前的天气情况', '\\b^天气\\s(\\S+)', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (4, 1, '地理 城市', '查询城市的地理信息', '如使用「地理 北京」来查询北京的地理信息', '\\b^地理\\s(\\S+)', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (5, 1, '清除缓存', '清除机器人的缓存', '使用指令清除机器人的一些缓存内容', '\\b清除缓存\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (6, 1, '更新资源', '更新机器人用到的资源', '更新机器人用到的资源，可以保持机器人的功能为最新', '\\b更新资源\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (7, 1, '无量姬状态', '获取无量姬的运行状态', '获取无量姬在当前系统中的运行状态', '\\b无量姬状态\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (8, 1, '重载指令', '重新加载指令列表', '重新加载指令列表，刷新可用指令', '\\b重载指令\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (9, 1, '日活', '查看无量姬当前的日活~', '查看无量姬当前的日活~', '\\b(日活)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (10, 2, '更新词库', '更新Warframe词库', '更新Warframe中英文翻译的词库', '\\b更新词库\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (11, 2, 'wm', 'wm市场物品查询', '使用指令「wm 物品名」来查询Warframe Market上的物品订单\n如「wm Saryn Prime Set」，支持中文模糊查询', '(?i)\\bwm\\s*(\\S+.*)$', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (12, 2, 'wr', 'wm市场紫卡查询', '使用指令「wr 物品名 词条」来查询Warframe Market上的紫卡订单\n如「wr 战刃 暴击 无负」，支持中文模糊查询', '(?i)\\b(wr|wmr)\\s*(\\S+.*)$', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (13, 2, 'wl', 'wm市场玄骸查询', '使用指令「wl 物品名 有无幻纹 属性 伤害数值等」来查询Warframe Market上的玄骸订单\n如「wl 信条·冷冻光束步枪 火 无 60」，支持中文模糊查询', '(?i)\\bwl\\s*(\\S+.*)$', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (14, 2, '裂缝', '裂缝信息', '获取 Warframe 普通裂缝的信息', '\\b(裂缝|裂隙)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (15, 2, '钢铁裂缝', '钢铁裂缝信息', '获取 Warframe 钢铁裂缝的信息', '\\b(钢铁裂缝|钢铁裂隙)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (16, 2, '九重天', '九重天裂缝信息', '获取 Warframe 九重天裂缝信息', '\\b九重天\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (17, 2, '奸商', '虚空商人信息', '获取 Warframe 虚空商人信息', '\\b奸商\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (18, 2, '钢铁', '钢铁之路信息', '获取 Warframe 钢铁之路信息', '\\b钢铁\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (19, 2, '突击', '查看每日突击', '获取 Warframe 每日突击信息', '\\b突击\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (20, 2, '执刑官', '查看每周突击信息', '获取 Warframe 每周突击信息', '\\b(执(?:行|刑)官)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (21, 2, '电波', '查看午夜电波信息', '查询 Warframe 午夜电波信息', '\\b(电波|午夜电波)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (22, 2, '火卫二状态', '查看火卫二平原状态', '查询 Warframe 火卫二平原当前状态\n可以使用的指令有 火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原', '\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (23, 2, '希图斯状态', '查看希图斯平原状态', '查询 Warframe 地球平原当前状态\n可以使用指令 地球状态|地球平原状态|希图斯状态|夜灵平原状态 进行查询', '\\b(地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (24, 2, '地球状态', '查看地球昼夜循环状态', '查询 Warframe 地球昼夜循环当前状态\n可以使用指令 地球状态|地球平原状态|希图斯状态|夜灵平原状态 进行查询', '\\b(地球状态|地球时间|地球)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (25, 2, '金星状态', '查看金星平原状态', '查询 Warframe 金星平原当前状态\n可以使用指令 金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜 进行查询', '\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (26, 2, '双衍状态', '查看双衍平原当前状态', '查询 Warframe 双衍平原状态 可以使用指令 双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态 进行查询', '\\b(双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (27, 2, '平原', '查看全部平原的状态信息', '查询 Warframe 所有平原当前状态\n可以使用指令 平原|全部平原|平原时间 进行查询', '\\b(平原|全部平原|平原时间)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (28, 2, '入侵', '查询当前的入侵信息', '查询 Warframe 当前的入侵信息', '\\b入侵\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (29, 2, '本周灵化', '查看本周回廊的灵化信息', '查询 Warframe 本周回廊信息\n可以使用指令 本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊 进行查询', '\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (30, 2, '紫卡排行', '查看今天的紫卡价格排行榜', '查询 Warframe 今天的紫卡价格排行榜\n可以使用指令 紫卡价格|紫卡排行|紫卡 进行查询', '\\b(紫卡价格|紫卡排行|紫卡)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (31, 2, '增幅器', '查看增幅器部件', '查询 Warframe 增幅器序号与功能\n可以使用指令 增幅器|指挥官|指挥官武器|amp 进行查询', '(?i)\\b(增幅器|指挥官|指挥官武器|amp)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (32, 2, '希图斯幽魂', '查看幽魂快速获取路线', '查看 Warframe 在希图斯平原快速获取幽魂的路线\n可以使用指令 幽魂|希图斯幽魂 进行查询', '(?i)\\b(幽魂|希图斯幽魂)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (33, 2, '信条/终幕轮换', '查看信条和终幕武器轮换时间', '查看 Warframe 在佩兰数列购买的武器和终幕武器的轮换时间\n可以使用指令 信条|终幕|终幕轮换|信条轮换 进行查询', '(?i)\\b(信条|终幕|终幕轮换|信条轮换)\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (34, 3, '重开', '开始人生重开游戏', '人生没有彩排，只有重开\n人生重开模拟器启动指令，使用「重开」来进行游戏', '\\b重开\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (35, 3, '天赋', '选择人生重开的天赋', '开始人生重开后选择天赋，使用「天赋 1 2 3」的格式来选择天赋\n使用一个空格隔开选择的天赋', '\\b^天赋\\s+(\\S+)(?:\\s+\\S+)*', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (36, 3, '分配', '为人生重开分配属性', '选择完天赋后，使用「分配 颜值 智力 体质 家境」的格式来分配属性\n使用一个空格隔开选择的属性', '\\b^分配\\s+(\\S+)(?:\\s+\\S+)*', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (37, 3, '随机', '随机获取属性', '选择完天赋后，使用「随机」来获取随机属性', '\\b随机\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (38, 3, '继续', '继续人生重开游戏', '完成属性分配后，使用「继续 继续的步数」来进行快速重开人生\n也可以使用「继续」进行下一步人生', '\\b^继续(\\s+\\d+)?\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (39, 4, '全部卡池', '查看原神所有可用的卡池', '查看原神模拟抽卡中所有可用的卡池', '\\b全部卡池\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (40, 4, '启用卡池', '更改模拟抽卡启用的卡池', '如使用「启用卡池 杯装之诗-1.0」来启用这个卡池\n也可以使用「启用卡池 1.0」或「启用卡池 1.0 武器」\n可以选择 角色 武器 常驻\n具体卡池可以通过指令「全部卡池」来查看', '\\b^启用卡池\\s*(\\S+)', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (41, 4, '十连', '进行原神模拟抽卡', '进行原神模拟抽卡10连抽', '\\b十连\\b', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (42, 4, '历史记录\r', '查看原神抽卡历史记录', '使用「历史记录 你的原神uid」来查询你存在机器人上的抽卡记录\n如果已经绑定过机器人，可以直接使用「历史记录」来查询自己的抽卡记录', '\\b^历史记录\\s*(\\S*)?', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (43, 4, '抽卡记录', '获取原神抽卡记录', '直接使用指令「抽卡记录」，使用米游社扫码来获取你的原神抽卡记录\n若私聊向机器人使用「抽卡记录 获取的抽卡链接」也同样可用获取到抽卡记录\n抽卡链接方式见指令「抽卡链接」', '\\b^抽卡记录\\s*(\\S.*)', 1, 0, '2025-04-06 10:44:40', NULL);
INSERT INTO `bot_directives` VALUES (44, 4, '抽卡链接', '获取原神抽卡链接', '复制并且黏贴到电脑Powershell中\n打开原神抽卡记录页面，Ctrl+A全选，Ctrl+C复制，然后在Powershell中Ctrl+V粘贴并回车\n即可获取到抽卡链接', '\\b抽卡链接\\b', 1, 0, '2025-04-06 10:44:40', NULL);

SET FOREIGN_KEY_CHECKS = 1;

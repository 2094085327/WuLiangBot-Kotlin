SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for blacklist
-- ----------------------------
DROP TABLE IF EXISTS `blacklist`;
CREATE TABLE `blacklist`  (
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bot_config
-- ----------------------------
DROP TABLE IF EXISTS `bot_config`;
CREATE TABLE `bot_config`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配置名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配置键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '配置值',
  `status` tinyint(1) NULL DEFAULT NULL COMMENT '状态',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_status` tinyint(1) NULL DEFAULT 0 COMMENT '删除状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

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
  `enable` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用  0为否 1为是',
  `del_status` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除  0为否 1为是',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 48 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bot_log
-- ----------------------------
DROP TABLE IF EXISTS `bot_log`;
CREATE TABLE `bot_log`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '日志id',
  `business_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '方法描述',
  `class_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求对应的类全包名',
  `method_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求对应的全方法名',
  `cmd_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指令文本',
  `event_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '事件类型',
  `group_id` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发指令的群',
  `user_id` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发指令的用户',
  `bot_id` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '接受指令的bot',
  `cost_time` int(11) NULL DEFAULT NULL COMMENT '执行指令花费的时间（ms）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '日志创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1717 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bot_platform_stats
-- ----------------------------
DROP TABLE IF EXISTS `bot_platform_stats`;
CREATE TABLE `bot_platform_stats`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `timestamp` datetime NULL DEFAULT NULL COMMENT '时间戳',
  `count` int(11) NULL DEFAULT NULL COMMENT '时间范围内消息量',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `timestamp`(`timestamp` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1111 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gacha
-- ----------------------------
DROP TABLE IF EXISTS `gacha`;
CREATE TABLE `gacha`  (
  `user_key` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '用户标识',
  `today_expire` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '当日失效时间',
  `week_expire` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '本周失效时间',
  `star` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '物品',
  `permanent` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `role` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL,
  `weapon` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gacha_log_info
-- ----------------------------
DROP TABLE IF EXISTS `gacha_log_info`;
CREATE TABLE `gacha_log_info`  (
  `uid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `gacha_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `item_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `count` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `time` datetime NULL DEFAULT NULL,
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `lang` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `item_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `rank_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `uigf_gacha_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for genshin_info
-- ----------------------------
DROP TABLE IF EXISTS `genshin_info`;
CREATE TABLE `genshin_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `uid` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户UID',
  `qqid` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户QQID',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原神游戏昵称',
  `cookie` varchar(1500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原神签到cookie',
  `stoken` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '抽卡分析stoken',
  `push` int(11) NULL DEFAULT NULL COMMENT '是否推送',
  `deletes` int(11) NULL DEFAULT NULL COMMENT '是否废弃',
  `status` int(11) NULL DEFAULT NULL COMMENT '0为失效，1为可用',
  `update_time` datetime NULL DEFAULT current_timestamp() COMMENT '数据插入或更新的时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lifeRestartInfo
-- ----------------------------
DROP TABLE IF EXISTS `lifeRestartInfo`;
CREATE TABLE `lifeRestartInfo`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `real_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `times` int(11) NULL DEFAULT NULL COMMENT '玩家进行游戏的次数',
  `cachv` int(11) NULL DEFAULT NULL COMMENT '达成的成就数',
  `achv` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '达成的成就',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `real_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `genshin_uid` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wf_market_item
-- ----------------------------
DROP TABLE IF EXISTS `wf_market_item`;
CREATE TABLE `wf_market_item`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'wm物品id',
  `en_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物品英文名',
  `zh_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物品中文名',
  `url_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物品URL名',
  `game_ref` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Warframe游戏内部引用路径',
  `tags` json NULL COMMENT 'Warframe Market v2物品标签',
  `max_rank` int NULL COMMENT '最高等级，NULL表示无等级',
  `max_amber_stars` int NULL COMMENT '雕像满星琥珀容量',
  `max_cyan_stars` int NULL COMMENT '雕像满星青蓝容量',
  `use_count` int(10) NULL DEFAULT NULL COMMENT '使用次数',
  `ducats` int(10) NULL DEFAULT NULL COMMENT '物品对应杜卡德数量',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

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
-- Table structure for wf_riven
-- ----------------------------
DROP TABLE IF EXISTS `wf_riven`;
CREATE TABLE `wf_riven`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键ID',
  `url_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'url名',
  `r_group` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分组',
  `req_mastery_rank` float NULL DEFAULT NULL COMMENT '精通段位',
  `riven_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '紫卡类型',
  `zh` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '中文名',
  `en` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '英文名',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `id_en_zh_url`(`id` ASC, `url_name` ASC, `zh` ASC, `en` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wf_riven_attribute
-- ----------------------------
DROP TABLE IF EXISTS `wf_riven_attribute`;
CREATE TABLE `wf_riven_attribute`  (
  `id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Warframe Market 属性ID',
  `url_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API slug及拍卖查询参数',
  `game_ref` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '游戏内部属性标识',
  `r_group` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '属性分组',
  `prefix` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '紫卡名称前缀',
  `suffix` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '紫卡名称后缀',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '属性数值单位',
  `zh` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '中文名',
  `en` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '英文名',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wf_riven_attribute_url_name`(`url_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Warframe紫卡属性词库' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for current riven samples
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_riven_sample (
    auction_id varchar(24) NOT NULL COMMENT 'Warframe Market挂牌ID；样本唯一标识',
    weapon_slug varchar(128) NOT NULL COMMENT 'Warframe Market武器标识，例如magistar',
    seller_id varchar(24) NOT NULL COMMENT 'Warframe Market卖家ID',
    price int NOT NULL COMMENT '直接出售挂牌价格，单位为白金；不是成交价',
    mod_rank int NOT NULL COMMENT '紫卡模组等级，范围0至8',
    rerolls int NOT NULL COMMENT '紫卡洗练次数，0表示未洗',
    mastery int NOT NULL COMMENT '使用紫卡所需的精通段位',
    polarity varchar(32) NOT NULL COMMENT '紫卡极性标识',
    attributes_json longtext NOT NULL COMMENT '规范化词条JSON，包含属性标识、正负方向和原始数值',
    raw_item_json longtext NOT NULL COMMENT '上游原始紫卡item JSON；数值不自动按等级换算',
    created_at bigint NOT NULL COMMENT '上游挂牌创建时间，UTC毫秒时间戳',
    updated_at bigint NOT NULL COMMENT '上游挂牌最近修改时间，UTC毫秒时间戳；不等同于改价时间',
    seller_last_seen bigint DEFAULT NULL COMMENT '卖家最近活跃时间，UTC毫秒时间戳；NULL表示未知',
    seller_status varchar(32) NOT NULL COMMENT '观察时卖家状态，例如online、ingame、offline',
    active tinyint(1) NOT NULL COMMENT '观察时挂牌是否可用，1为可用，0为不可用；不表示已成交',
    first_observed_at bigint NOT NULL COMMENT '本系统首次观察该挂牌的时间，UTC毫秒时间戳',
    last_observed_at bigint NOT NULL COMMENT '本系统最近实际观察时间，UTC毫秒时间戳；读取缓存不刷新',
    PRIMARY KEY (auction_id),
    KEY idx_riven_sample_weapon (weapon_slug, last_observed_at),
    KEY idx_riven_sample_expiry (last_observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='紫卡直接出售挂牌的当前样本；用于近期挂牌参考';

CREATE TABLE IF NOT EXISTS wf_riven_sample_query (
    query_key char(64) NOT NULL COMMENT '规范化查询参数的SHA-256摘要；查询条件唯一标识',
    weapon_slug varchar(128) NOT NULL COMMENT 'Warframe Market武器标识，例如magistar',
    query_json longtext NOT NULL COMMENT '规范化查询条件JSON，包含词条、负面限制和出售口径',
    source varchar(16) NOT NULL COMMENT '查询来源：USER用户查询，ANALYSIS紫卡分析，WARMUP预热',
    last_attempt_at bigint NOT NULL COMMENT '最近尝试查询的时间，UTC毫秒时间戳',
    last_success_at bigint DEFAULT NULL COMMENT '最近成功查询的时间，UTC毫秒时间戳；NULL表示未成功',
    raw_count int NOT NULL DEFAULT 0 COMMENT '最近成功响应中的原始挂牌数',
    stored_count int NOT NULL DEFAULT 0 COMMENT '最近成功响应中通过校验并按挂牌ID去重后的数量',
    skipped_json longtext NOT NULL COMMENT '跳过原因及各原因对应数量的JSON',
    last_error varchar(1000) DEFAULT NULL COMMENT '最近查询错误摘要；成功后清空',
    retry_after bigint NOT NULL DEFAULT 0 COMMENT '本条件失败冷却结束时间，UTC毫秒时间戳；0表示无冷却',
    PRIMARY KEY (query_key),
    KEY idx_riven_query_weapon (weapon_slug, last_attempt_at),
    KEY idx_riven_query_expiry (last_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='紫卡按条件查询状态；保存成功时间、空结果和失败冷却';

CREATE TABLE IF NOT EXISTS wf_market_daily_trade_average (
    item_id VARCHAR(50) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    trade_day DATE NOT NULL COMMENT '上一个完整UTC日',
    variant_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    variant_json JSON NOT NULL COMMENT '统计接口实际返回的等级/规格，不假定缺失的subtype',
    `rank` INT NOT NULL DEFAULT -1,
    min_price DECIMAL(18,6) NULL COMMENT '同日同规格最低成交价；旧记录未知为NULL',
    max_price DECIMAL(18,6) NULL COMMENT '同日同规格最高成交价；旧记录未知为NULL',
    average_price DECIMAL(18,6) NOT NULL COMMENT '目标UTC日closed记录wa_price（relics.run归档）',
    volume BIGINT NOT NULL COMMENT '同日同规格WM关闭订单统计量，不是在线挂单数',
    fetched_at BIGINT NOT NULL COMMENT 'UTC Unix毫秒',
    PRIMARY KEY (item_id, platform, trade_day, variant_key),
    KEY idx_market_trade_ranking (platform, trade_day, average_price),
    KEY idx_market_trade_expiry (trade_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;
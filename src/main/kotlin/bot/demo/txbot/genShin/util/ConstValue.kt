package bot.demo.txbot.genShin.util


/**
 * @description: 原神用到的一些常量
 * @author Nature Zero
 * @date 2024/4/3 8:17
 */
// 缓存路径
const val CACHE_PATH = "resources/gachaCache"

// 图片缓存路径
const val IMG_CACHE_PATH = "resources/imageCache"

// 当前启用的模拟抽卡卡池信息
const val GACHA_JSON = "resources/genShin/defSet/gacha/gacha.json"

// 历史卡池信息
const val POOL_JSON = "resources/genShin/defSet/gacha/pool.json"

// 各版本卡池新增常驻物品信息
const val New_ADD = "resources/genShin/defSet/gacha/newAdd.json"

// 角色元素信息
const val ROLE_JSON = "resources/genShin/defSet/element/role.yaml"

// 武器类型信息
const val WEAPON_JSON = "resources/genShin/defSet/element/weapon.yaml"

// 角色图片资源
const val ROLE_IMG = "resources/genShin/GenShinImg/role/"

// 武器图片资源
const val WEAPON_IMG = "resources/genShin/GenShinImg/weapons/"

// 抽卡记录链接获取接口
const val GACHA_LINK = "https://hk4e-api.mihoyo.com/event/gacha_info/api/getGachaLog?"

// 抽卡记录缓存路径
const val GACHA_LOG_FILE = "resources/gachaCache/gachaLog-"
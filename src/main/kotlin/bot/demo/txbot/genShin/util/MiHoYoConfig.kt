package bot.demo.txbot.genShin.util

import bot.demo.txbot.other.RESOURCES_PATH

/**
 * @description: 原神用到的一些常量
 * @author Nature Zero
 * @date 2024/4/3 8:17
 */
const val UIGF_VERSION = "v3.0" // 抽卡数据统一标准版本

const val GACHA_JSON = "resources/genShin/defSet/gacha/gacha.json" // 当前启用的模拟抽卡卡池信息
const val POOL_JSON = "resources/genShin/defSet/gacha/pool.json" // 历史卡池信息
const val New_ADD = "resources/genShin/defSet/gacha/newAdd.json" // 各版本卡池新增常驻物品信息
const val ROLE_YAML = "resources/genShin/defSet/element/role.yaml" // 角色元素信息
const val WEAPON_YAML = "resources/genShin/defSet/element/weapon.yaml" // 武器类型信息

const val ROLE_IMG = "resources/genShin/GenShinImg/role/" // 角色图片资源
const val WEAPON_IMG = "resources/genShin/GenShinImg/weapons/" // 武器图片资源

const val GACHA_CACHE_PATH = "$RESOURCES_PATH/gachaCache" // 缓存路径
const val GACHA_LOG_FILE = "$GACHA_CACHE_PATH/gachaLog-" // 抽卡记录缓存路径
const val GACHA_LOG_IMPORT = "resources/genShin/ImportGacha" // 抽卡记录导入路径

const val ANDROID_HOST = "https://api-takumi.mihoyo.com/" // 安卓请求host
const val ANDROID_RECORD = "https://api-takumi-record.mihoyo.com/" // 安卓请求host record
const val IOS_HOST = "https://api-os-takumi.mihoyo.com/" // IOS请求host
const val IOS_RECORD = "https://bbs-api-os.mihoyo.com/" // IOS请求host record

const val APP_VERSION = "2.71.1" // 应用版本
const val SLAT_LK2 = "EJncUPGnOHajenjLhBOsdpwEMZmiCmQX" // 请求头加密slat,KL2版本
const val GACHA_LOG_URL = "https://public-operation-hk4e.mihoyo.com/gacha_info/api/getGachaLog" // 抽卡记录链接
const val AUTHKEY_B = "${ANDROID_HOST}binding/api/genAuthKey" // 获取authkeyB链接
const val ACCOUNT_INFO = "${ANDROID_HOST}binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn" // 获取账号信息链接
const val QR_CODE = "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch" // 获取二维码链接
const val QR_CODE_STATUS = "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query" // 检查二维码状态链接

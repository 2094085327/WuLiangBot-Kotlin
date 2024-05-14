package bot.demo.txbot.genShin.util

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

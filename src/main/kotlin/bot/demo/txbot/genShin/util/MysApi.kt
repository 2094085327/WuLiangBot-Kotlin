package bot.demo.txbot.genShin.util

import org.springframework.stereotype.Component
import java.util.*


/**
 * @description: 米游社的API接口数据
 * @author Nature Zero
 * @date 2024/7/21 下午10:31
 */
@Component
class MysApi {
    data class ApiEndpoint(
        val url: String,
        val query: String? = null,
        val body: MutableMap<String, Any?>? = null,
        val sign: Boolean = false
    )

    companion object {
        val uid: String? = null
        val server: String = getServerMys()

        private fun getServerMys(): String {
            return when (uid?.get(0)) {
                '1', '2', '3' -> "cn_gf01" // 官服
                '5' -> "cn_qd01" // B服
                '6' -> "os_usa" // 美服
                '7' -> "os_euro" // 欧服
                '8' -> "os_asia" // 亚服
                '9' -> "os_cht" // 港澳台服
                else -> "cn_gf01"
            }
        }
    }

    val androidServers = arrayListOf("cn_gf01", "cn_qd01", "prod_gf_cn", "prod_qd_cn")
    private val iosServers = arrayListOf("os_usa", "os_euro", "os_asia", "os_cht")
    private val urlMap: MutableMap<String, ApiEndpoint> = WeakHashMap()
    private val serverType: ServerType = determineServerType()

    enum class ServerType(val host: String, val hostRecord: String) {
        ANDROID(ANDROID_HOST, ANDROID_RECORD),
        IOS(IOS_HOST, IOS_RECORD),
    }

    /**
     * 根据服务器类型判断使用的设备类型
     *
     * @return 服务器类型
     */
    private fun determineServerType(): ServerType {
        return when (server) {
            in androidServers -> ServerType.ANDROID
            in iosServers -> ServerType.IOS
            else -> ServerType.ANDROID // 默认值
        }
    }

    /**
     * 通过哈希值生成缓存，保证Data处于最新状态
     * 当 data 发生变化时，会重新生成缓存
     *
     * @param key 缓存名称
     * @param data 数据
     * @return 用于缓存的Key
     */
    private fun generateCacheKey(key: String, data: MutableMap<String, Any?>?): String {
        return key + data.hashCode().toString()
    }

    /**
     * 获取API接口相关数据
     *
     * @param key 接口名称
     * @param data 传入的参数
     * @return 接口数据
     */
    fun getUrlApiEndpoint(key: String, data: MutableMap<String, Any?>? = null): ApiEndpoint? {
        val cacheKey = generateCacheKey(key, data)

        // 检查缓存是否存在
        if (!urlMap.containsKey(cacheKey)) {
            data?.let { buildUrl(key, it)?.let { endpoint -> urlMap[cacheKey] = endpoint } }
        }
        return urlMap[cacheKey]
    }

    /**
     * 构建API接口相关数据
     *
     * @param key 接口名称
     * @param data 传入的参数
     * @return 构建完成的接口数据
     */
    private fun buildUrl(key: String, data: MutableMap<String, Any?> = mutableMapOf()): ApiEndpoint? {
        return when (key) {
            "index" -> ApiEndpoint(
                url = "${serverType.hostRecord}game_record/app/genshin/api/index",
                query = "role_id=$uid&server=$server"
            )

            "spiralAbyss" -> ApiEndpoint(
                url = "${serverType.hostRecord}game_record/app/genshin/api/spiralAbyss",
                query = "role_id=$uid&schedule_type=2&server=$server"
            )

            "character" -> ApiEndpoint(
                url = "${serverType.hostRecord}game_record/app/genshin/api/character",
                body = mutableMapOf("role_id" to uid, "server" to server)
            )

            "dailyNote" -> ApiEndpoint(
                url = "${serverType.hostRecord}game_record/app/genshin/api/dailyNote",
                query = "role_id=$uid&server=$server"
            )

            "bbs_sign_info" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/bbs_sign_reward/info"
                else "${BASE_OS_URL}/event/sol/info",
                query = if (serverType == ServerType.ANDROID) "act_id=e202009291139501&region=$server&uid=$uid"
                else "act_id=e202102251931481&region=${server}&uid=${uid}",
                sign = true
            )

            "bbs_sign_home" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/bbs_sign_reward/home" else "${BASE_OS_URL}/event/sol/home",
                query = if (serverType == ServerType.ANDROID) "act_id=e202009291139501&region=$server&uid=$uid" else "act_id=e202102251931481&region=${server}&uid=${uid}",
                sign = true
            )

            "bbs_sign" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/bbs_sign_reward/sign" else "${BASE_OS_URL}/event/sol/sign",
                body = if (serverType == ServerType.ANDROID) mutableMapOf(
                    "act_id" to "e202009291139501",
                    "region" to server,
                    "uid" to uid
                ) else mutableMapOf("act_id" to "e202102251931481", "region" to server, "uid" to uid),
                sign = true
            )

            "detail" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/e20200928calculate/v1/sync/avatar/detail" else "${PUBLIC_API_URL}event/calculate/v1/sync/avatar/detail",
                query = if (serverType == ServerType.ANDROID) "uid=$uid&region=$server&avatar_id=${data["avatar_id"]}" else "lang=zh-cn&uid=${uid}&region=${server}&avatar_id=${data["avatar_id"]}"
            )

            "ys_ledger" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "https://hk4e-api.mihoyo.com/event/ys_ledger/monthInfo" else "https://hk4e-api-os.mihoyo.com/event/ysledgeros/month_info",
                query = if (serverType == ServerType.ANDROID) "month=${data["month"]}&bind_uid=$uid&bind_region=$server" else "lang=zh-cn&month=${data["month"]}&uid=${uid}&region=${server}"
            )

            "compute" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/e20200928calculate/v2/compute" else "${PUBLIC_API_URL}event/calculateos/compute",
                body = data
            )

            "blueprintCompute" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/e20200928calculate/v1/furniture/compute" else "${PUBLIC_API_URL}event/calculateos/furniture/compute",
                body = if (serverType == ServerType.ANDROID) data else data.apply { "lang" to "zh-cn" }
            )

            "blueprint" -> ApiEndpoint(
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/e20200928calculate/v1/furniture/blueprint" else "${PUBLIC_API_URL}event/calculateos/furniture/blueprint",
                query = if (serverType == ServerType.ANDROID) "share_code=${data["share_code"]}&region=$server" else "share_code=${data["share_code"]}&region=${server}&lang=zh-cn"
            )

            "avatarSkill" -> ApiEndpoint( // 角色技能
                url = if (serverType == ServerType.ANDROID) "${serverType.host}event/e20200928calculate/v1/avatarSkill/list" else "${PUBLIC_API_URL}event/calculateos/avatar/skill_list",
                query = if (serverType == ServerType.ANDROID) "share_code=${data["share_code"]}&region=$server" else "lang=zh-cn&avatar_id=${data["avatar_id"]}"
            )

            "basicInfo" -> ApiEndpoint( // 七圣召唤数据
                url = "${serverType.hostRecord}game_record/app/genshin/api/gcg/basicInfo",
                query = "role_id=$uid&server=$server"
            )

            "getStokenUrl" -> ApiEndpoint(
                url = "${serverType.host}auth/api/getMultiTokenByLoginTicket",
                query = "login_ticket=${data["login_ticket"]}&token_types=3&uid=${uid}"
            )

            "getCookieTokenUrl" -> ApiEndpoint(
                url = "${serverType.host}auth/api/getCookieAccountInfoBySToken",
                query = "stoken=${data["stoken"]}&uid=${uid}"
            )

            "getStokenByGameToken" -> ApiEndpoint(
                url = "${serverType.host}account/ma-cn-session/app/getTokenByGameToken",
                body = mutableMapOf("account_id" to data["accountId"], "game_token" to data["gameToken"]),
            )

            "getTokenBySToken" -> ApiEndpoint(
                url = "${serverType.host}account/ma-cn-session/app/getTokenBySToken",
            )

            "getCookieByGameToken" -> ApiEndpoint(
                url = "${serverType.host}auth/api/getCookieAccountInfoByGameToken",
                query = "account_id=${data["accountId"]}&game_token=${data["gameToken"]}"
            )

            "getHk4eByCookieToken" -> ApiEndpoint(
                url = "${serverType.host}common/badge/v1/login/account",
                body = mutableMapOf("region" to data["server"], "uid" to data["uid"], "game_biz" to "hk4e_cn"),
            )

            "getAccountInfo" -> ApiEndpoint(
                url = "https://api-takumi.miyoushe.com/binding/api/getUserGameRolesByStoken",
                sign = true
            )

            "authKeyA" -> ApiEndpoint(
                url = "https://api-takumi.miyoushe.com/account/auth/api/genAuthKey",
                body = mutableMapOf("game_biz" to "bbs_cn")
            )

            "authKeyB" -> ApiEndpoint(
                url = AUTHKEY_B,
                body = mutableMapOf(
                    "auth_appid" to "webview_gacha",
                    "game_biz" to "hk4e_cn",
                    "game_uid" to uid,
                    "region" to server
                ),
                sign = true
            )

            "gachaLog" -> ApiEndpoint(
                url = GACHA_LOG_URL,
                query = "authkey_ver=1&authkey=${data["authkey"]}&lang=zh-cn&size=${data["size"]}&end_id=${data["end_id"]}&page=${data["page"]}&gacha_type=${data["gacha_type"]}"
            )

            "accountInfo" -> ApiEndpoint(
                url = ACCOUNT_INFO,
            )

            "qrCode" -> ApiEndpoint(
                url = QR_CODE,
                query = "app_id=4&device=${data["device"]}"
            )

            "qrCodeStatus" -> ApiEndpoint(
                url = QR_CODE_STATUS,
                body = mutableMapOf(
                    "app_id" to "4",
                    "device" to data["device"],
                    "ticket" to data["ticket"],
                ),
                sign = true
            )

            else -> null
        }
    }
}
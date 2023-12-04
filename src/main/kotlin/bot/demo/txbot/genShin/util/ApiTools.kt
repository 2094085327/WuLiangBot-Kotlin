package bot.demo.txbot.genShin.util

/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/20 10:43
 *@User 86188
 */
class ApiTools(
    val uid: String, val server: String,
) {

    fun getUrlMap(data: MutableMap<String, Any> = mutableMapOf()): MutableMap<String, Map<String, Any>> {
        var host = ""
        var hostRecord = ""
        val gachaHost = "https://webstatic.mihoyo.com"
        val android: ArrayList<String> = arrayListOf("cn_gf01", "cn_qd01", "prod_gf_cn", "prod_qd_cn")
        val ios: ArrayList<String> = arrayListOf("os_usa", "os_euro", "os_asia", "os_cht")
        if (android.contains(server)) {
            host = "https://api-takumi.mihoyo.com/"
            hostRecord = "https://api-takumi-record.mihoyo.com/"
        } else if (ios.contains(server)) {
            host = "https://api-os-takumi.mihoyo.com/"
            hostRecord = "https://bbs-api-os.mihoyo.com/"
        }
        val urlMap = mutableMapOf<String, Map<String, Any>>(
            //首页宝箱
            "index" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/index",
                "query" to "role_id=$uid&server=$server"
            ),
            // 深渊
            "spiralAbyss" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/spiralAbyss",
                "query" to "role_id=$uid&schedule_type=${2}&server=$server"
            ),
            // 角色
            "character" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/character",
                "body" to mutableMapOf("role_id" to uid, "server" to server)
            ),
            // 树脂
            "dailyNote" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/dailyNote",
                "query" to "role_id=$uid&server=$server"
            ),
            // 签到信息
            "bbs_sign_info" to mutableMapOf(
                "url" to "${host}event/bbs_sign_reward/info",
                "query" to "act_id=e202009291139501&region=$server&uid=$uid",
                "sign" to true
            ),
            // 签到奖励
            "bbs_sign_home" to mutableMapOf(
                "url" to "${host}event/bbs_sign_reward/home",
                "query" to "act_id=e202009291139501&region=$server&uid=$uid",
                "sign" to true
            ),
            // 签到
            "bbs_sign" to mutableMapOf(
                "url" to "${host}event/bbs_sign_reward/sign",
                "body" to mutableMapOf("act_id" to "e202009291139501", "region" to server, "uid" to uid),
                "sign" to true
            ),
            // 详情
            "detail" to mutableMapOf(
                "url" to "${host}event/e20200928calculate/v1/sync/avatar/detail",
                "query" to "uid=$uid&region=$server&avatar_id=${data["avatar_id"]}"
            ),
            // 札记
            "ys_ledger" to mutableMapOf(
                "url" to "https://hk4e-api.mihoyo.com/event/ys_ledger/monthInfo",
                "query" to "month=${data["month"]}&bind_uid=$uid&bind_region=$server"
            ),
            // 养成计算器
            "compute" to mutableMapOf(
                "url" to "${host}event/e20200928calculate/v2/compute",
                "body" to data
            ),
            // 蓝图计算
            "blueprintCompute" to mutableMapOf(
                "url" to "${host}event/e20200928calculate/v1/furniture/compute",
                "body" to data
            ),
            // 蓝图计算
            "blueprint" to mutableMapOf(
                "url" to "${host}event/e20200928calculate/v1/furniture/blueprint",
                "query" to "share_code=${data["share_code"]}&region=$server"
            ),
            "avatarSkill" to mutableMapOf(
                "url" to "${host}event/e20200928calculate/v1/avatarSkill/list",
                "query" to "avatar_id=${data["avatar_id"]}"
            ),
            // 角色技能
            "basicInfo" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/gcg/basicInfo",
                "query" to "role_id=$uid&server=$server"
            ),
            // 七圣召唤数据
            "basicInfo" to mutableMapOf(
                "url" to "${hostRecord}game_record/app/genshin/api/gcg/basicInfo",
                "query" to "role_id=${uid}&server=${server}"
            ),
            // 获取stoken 此处uid为米游社uid，cookie中字段为login_uid
            "GET_STOKEN_URL" to mutableMapOf(
                "url" to "${host}auth/api/getMultiTokenByLoginTicket",
                "query" to "login_ticket=${data["login_ticket"]}&token_types=3&uid=${uid}"
            ),
            // 通过stoken获取cookie 此处uid为米游社uid，cookie中字段为login_uid
            "GET_COOKIE_TOKEN_URL" to mutableMapOf(
                "url" to "${host}auth/api/getCookieAccountInfoBySToken",
                "query" to "stoken=${data["stoken"]}&uid=${uid}"
            ),
            // 通过GameToken获取stoken
            "getStokenByGameToken" to mutableMapOf(
                "url" to "${host}account/ma-cn-session/app/getTokenByGameToken",
                "body" to mutableMapOf(
                    "account_id" to data["accountId"],
                    "game_token" to data["gameToken"]
                ),
            ),
            "getCookieByGameToken" to mutableMapOf(
                "url" to "${host}auth/api/getCookieAccountInfoByGameToken",
                "query" to "account_id=${data["accountId"]}&game_token=${data["gameToken"]}",
            ),
            "getHk4eByCookieToken" to mutableMapOf(
                "url" to "${host}common/badge/v1/login/account",
                "body" to mutableMapOf("region" to data["server"], "uid" to data["uid"], "game_biz" to "hk4e_cn"),
            ),
            "getAccountInfo" to mutableMapOf(
                "url" to "https://api-takumi.miyoushe.com/binding/api/getUserGameRolesByStoken",
                "sign" to true
            ),
            "authKeyA" to mutableMapOf(
                "url" to "https://api-takumi.miyoushe.com/account/auth/api/genAuthKey",
                "body" to mutableMapOf("game_biz" to "bbs_cn")
            ),
            // 获取抽卡分析用到的AuthKey
            "authKeyB" to mutableMapOf(
                "url" to "https://api-takumi.mihoyo.com/binding/api/genAuthKey",
                "body" to mutableMapOf(
                    "auth_appid" to "webview_gacha",
                    "game_biz" to "hk4e_cn",
                    "game_uid" to uid,
                    "region" to server
                ),
                "sign" to true
            ),
            // 卡池ID
            "gacha_Id" to mutableMapOf(
                "url" to "${gachaHost}/hk4e/gacha_info/cn_gf01/gacha/list.json"
            ),
            // 卡池详细信息
            "gacha_Info" to mutableMapOf(
                "url" to "${gachaHost}/hk4e/gacha_info/cn_gf01/${data["gachaId"]}/zh-cn.json"
            ),
            // 抽卡记录
            "gachaLog" to mutableMapOf(
                "url" to "https://hk4e-api.mihoyo.com/event/gacha_info/api/getGachaLog",
                "query" to "authkey_ver=1&authkey=${data["authkey"]}&lang=zh-cn&size=${data["size"]}&end_id=${data["end_id"]}&page=${data["page"]}&gacha_type=${data["gacha_type"]}"
            ),
            // 用户信息
            "accountInfo" to mutableMapOf(
                "url" to "https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn"
            ),
            // 获取二维码
            "qrCode" to mutableMapOf(
                "url" to "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch",
                "query" to "app_id=4&device=${data["device"]}"
//                "sign" to true
            ),
            // 二维码状态
            "qrCodeStatus" to mutableMapOf(
                "url" to "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query",
                "body" to mutableMapOf(
                    "app_id" to "4",
                    "device" to data["device"],
                    "ticket" to data["ticket"],
                ),
                "sign" to true
            )


        )

        if (server.startsWith("os")) {
            urlMap["bbs_sign_info"] =
                urlMap["bbs_sign_info"]?.plus("url" to "https://hk4e-api-os.hoyoverse.com/event/sol/info") ?: emptyMap()
            urlMap["bbs_sign_info"] =
                urlMap["bbs_sign_info"]?.plus("query" to "act_id=e202102251931481&region=${server}&uid=${uid}")
                    ?: emptyMap()
            urlMap["bbs_sign_home"] =
                urlMap["bbs_sign_home"]?.plus("url" to "https://hk4e-api-os.hoyoverse.com/event/sol/home") ?: emptyMap()
            urlMap["bbs_sign_home"] =
                urlMap["bbs_sign_home"]?.plus("query" to "act_id=e202102251931481&region=${server}&uid=${uid}")
                    ?: emptyMap()
            urlMap["bbs_sign"] =
                urlMap["bbs_sign"]?.plus("url" to "https://hk4e-api-os.hoyoverse.com/event/sol/sign") ?: emptyMap()
            urlMap["bbs_sign"] =
                urlMap["bbs_sign"]?.plus(
                    "body" to mutableMapOf(
                        "act_id" to "e202102251931481",
                        "region" to server,
                        "uid" to uid
                    )
                ) ?: emptyMap()
            urlMap["detail"] =
                urlMap["detail"]?.plus("url" to "https://sg-public-api.hoyolab.com/event/calculateos/sync/avatar/detail")
                    ?: emptyMap()
            urlMap["detail"] =
                urlMap["detail"]?.plus("query" to "lang=zh-cn&uid=${uid}&region=${server}&avatar_id=${data["avatar_id"]}")
                    ?: emptyMap()
            // 查询未持有的角色天赋
            urlMap["avatarSkill"] =
                urlMap["avatarSkill"]?.plus("url" to "https://sg-public-api.hoyolab.com/event/calculateos/avatar/skill_list")
                    ?: emptyMap()
            urlMap["avatarSkill"] =
                urlMap["avatarSkill"]?.plus("query" to "lang=zh-cn&avatar_id=${data["avatar_id"]}") ?: emptyMap()
            // 已支持养成计算
            urlMap["compute"] =
                urlMap["compute"]?.plus("url" to "https://sg-public-api.hoyolab.com/event/calculateos/compute")
                    ?: emptyMap()
            urlMap["blueprint"] =
                urlMap["blueprint"]?.plus("url" to "https://sg-public-api.hoyolab.com/event/calculateos/furniture/blueprint")
                    ?: emptyMap()
            urlMap["blueprint"] =
                urlMap["blueprint"]?.plus("query" to "share_code=${data["share_code"]}&region=${server}&lang=zh-cn")
                    ?: emptyMap()
            urlMap["blueprintCompute"] =
                urlMap["blueprintCompute"]?.plus("url" to "https://sg-public-api.hoyolab.com/event/calculateos/furniture/compute")
                    ?: emptyMap()
            urlMap["blueprintCompute"] =
                urlMap["blueprintCompute"]?.plus("body" to mutableMapOf("lang" to "zh-cn").plus(data)) ?: emptyMap()
            // 支持了国际服札记
            urlMap["ys_ledger"] =
                urlMap["ys_ledger"]?.plus("url" to "https://hk4e-api-os.mihoyo.com/event/ysledgeros/month_info")
                    ?: emptyMap()
            urlMap["ys_ledger"] =
                urlMap["ys_ledger"]?.plus("query" to "lang=zh-cn&month=${data["month"]}&uid=${uid}&region=${server}")
                    ?: emptyMap()
            urlMap["useCdk"] =
                urlMap["useCdk"]?.plus("url" to "https://sg-hk4e-api.hoyoverse.com/common/apicdkey/api/webExchangeCdkey")
                    ?: emptyMap()
            urlMap["useCdk"] =
                urlMap["useCdk"]?.plus("query" to "uid=${uid}&region=${server}&lang=zh-cn&cdkey=${data["cdk"]}&game_biz=hk4e_global")
                    ?: emptyMap()
        }
        return urlMap
    }
}
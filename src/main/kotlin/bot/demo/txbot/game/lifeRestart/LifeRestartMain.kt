package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.game.lifeRestart.datebase.LifeRestartService
import bot.demo.txbot.game.lifeRestart.restartResp.RestartRespBean
import bot.demo.txbot.game.lifeRestart.restartResp.RestartRespEnum
import bot.demo.txbot.game.lifeRestart.vo.JudgeItemVO
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher


/**
 * @description: 人生重开主文件
 * @author Nature Zero
 * @date 2024/2/14 18:54
 */
@Component
@ActionService
class LifeRestartMain(
    @Autowired private var lifeRestartService: LifeRestartService,
    @Autowired private val webImgUtil: WebImgUtil,
    @Autowired private val restartUtil: LifeRestartUtil,
    @Autowired private val redisService: RedisService
) {

    /**
     * 天赋格式正则
     *
     * @param talent 天赋字符串
     * @return Boolean
     */
    fun isTalentFormatValid(talent: String): Boolean {
        return Regex("""^(?:[1-9]|10)(?:\s(?:[1-9]|10))*$""").matches(talent)
    }


    enum class OperationType {
        CHOOSE_TALENT, ALREADY_CHOSE_TALENT, UNALLOCATED, ASSIGNED, CONTINUE
    }

    /**
     * 判断游戏状态
     *
     * @param userInfo 用户信息
     * @param operation 类型
     * @return
     */
    fun errorState(userInfo: LifeRestartUtil.UserInfo?, operation: OperationType): RestartRespBean {
        if (userInfo == null) return RestartRespBean.error(RestartRespEnum.GAME_NOT_START)

        fun checkTalentAssigned(): RestartRespBean {
            return when {
                userInfo.talent.isEmpty() -> RestartRespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                !userInfo.propertyDistribution -> RestartRespBean.error(RestartRespEnum.NO_ASSIGN_ATTRIBUTES)
                else -> RestartRespBean.success()
            }
        }

        return when (operation) {
            OperationType.CHOOSE_TALENT -> {
                if (userInfo.talent.isNotEmpty())
                    RestartRespBean.error(RestartRespEnum.ALL_READY_CHOSE_TALENT)
                else
                    RestartRespBean.success()
            }

            OperationType.ALREADY_CHOSE_TALENT -> {
                if (userInfo.talent.isEmpty())
                    RestartRespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                else
                    RestartRespBean.success()
            }

            OperationType.UNALLOCATED -> {
                if (userInfo.talent.isEmpty())
                    RestartRespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                else if (userInfo.propertyDistribution)
                    RestartRespBean.error(RestartRespEnum.ASSIGNED_ATTRIBUTES)
                else
                    RestartRespBean.success()
            }

            OperationType.ASSIGNED -> {
                if (!userInfo.propertyDistribution)
                    RestartRespBean.error(RestartRespEnum.NO_ASSIGN_ATTRIBUTES)
                else
                    RestartRespBean.success()
            }

            OperationType.CONTINUE -> checkTalentAssigned()
        }
    }


    /**
     * 更新时间并发送图片
     *
     * @param userInfo 用户信息
     * @param message 待发送的消息
     */
    fun updateAndSend(context: Context, userInfo: LifeRestartUtil.UserInfo, realId: String, message: String? = null) {
        val imageData = WebImgUtil.ImgData(
            imgName = "${realId}-LifeStart-${UUID.randomUUID()}",
            url = "http://localhost:16666/game/lifeRestart?game_userId=${realId}"
        )

        webImgUtil.sendNewImage(context, imageData)
        if (message != null) context.sendMsg(message)
        webImgUtil.deleteImg(imageData)

        // 更新用户缓存时间
        redisService.setValueWithExpiry("lifeRestart:userInfo:${realId}", userInfo, 5L, TimeUnit.MINUTES)
    }


    /**
     * 查找与输入值匹配的判定
     *
     * @param input 输入
     * @param judgeList 列表
     * @return 符合的判定
     */
    fun findJudgeForValue(input: Int, judgeList: List<JudgeItemVO>): JudgeItemVO? {
        // 检查列表是否为空
        if (judgeList.isEmpty()) {
            return null
        }

        for (i in judgeList.indices) {
            val current = judgeList[i]

            // 检查当前项的min是否为null
            val currentMin = current.min ?: continue

            // 如果是最后一项，只需要判断 input 是否大于等于当前 min
            if (i == judgeList.size - 1) {
                if (input >= currentMin) {
                    return current
                }
            } else {
                val next = judgeList[i + 1]
                // 检查下一项的min是否为null
                val nextMin = next.min ?: continue

                // 判断 input 是否在当前 min 和下一项 min 之间
                if (input in currentMin..<nextMin) {
                    return current
                }
            }
        }

        // 如果没有匹配到任何项，返回 null
        return null
    }

    /**
     * 发送游戏结束信息
     *
     * @param userInfo 用户信息
     */
    fun sendGameEnd(context: Context, userInfo: LifeRestartUtil.UserInfo) {
        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStart-${UUID.randomUUID()}",
            url = "http://localhost:16666/game/lifeRestart?game_userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(context, imageData)

        val effectStr = StringBuilder()
        listOf(CHR, INT, STR, MNY, SPR, AGE, SUM).forEach { key ->

            val mapper = jacksonObjectMapper()
            val rootNode: JsonNode = mapper.readValue(File(GRADE_JSONPATH))
            val judgeChrNode: JsonNode? = rootNode.path("judge").path(key)

            // 将特定部分转换为 List<JudgeItemVO>
            val propertyList: List<JudgeItemVO> = mapper.readValue(judgeChrNode.toString())

            when (key) {
                CHR -> effectStr.append(
                    "颜值:${userInfo.maxProperty[key]}  ${
                        rootNode["grade"]["judge_level"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                INT -> effectStr.append(
                    "智力:${userInfo.maxProperty[key]}  ${
                        if (findJudgeForValue(
                                userInfo.maxProperty[key]!!,
                                propertyList
                            )!!.judge!! < 7
                        ) rootNode["grade"]["judge_level"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue() else rootNode["grade"]["intelligence_judge"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                STR -> effectStr.append(
                    "体质:${userInfo.maxProperty[key]}  ${
                        if (findJudgeForValue(
                                userInfo.maxProperty[key]!!,
                                propertyList
                            )!!.judge!! < 7
                        ) rootNode["grade"]["judge_level"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue() else rootNode["grade"]["strength_judge"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                MNY -> effectStr.append(
                    "家境:${userInfo.maxProperty[key]}  ${
                        rootNode["grade"]["judge_level"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                SPR -> effectStr.append(
                    "快乐:${userInfo.maxProperty[key]}  ${
                        rootNode["grade"]["judge_level_color"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                AGE -> effectStr.append(
                    "年龄:${userInfo.age}  ${
                        rootNode["grade"]["age_judge"][(findJudgeForValue(
                            userInfo.age,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )

                SUM -> effectStr.append(
                    "总评:${userInfo.maxProperty[key]}  ${
                        rootNode["grade"]["judge_level"][(findJudgeForValue(
                            userInfo.maxProperty[key]!!,
                            propertyList
                        )!!.judge!!).toString()].textValue()
                    }\n"
                )
            }
        }
        context.sendMsg("游戏结束\n$effectStr")

        webImgUtil.deleteImg(imageData)
    }

    @AParameter
    @Executor(action = "重开")
    fun startRestart(context: Context) {
        // 初始化游戏数据
        val fetchResp = restartUtil.getFetchData()
        if (fetchResp.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(fetchResp.message)
            return
        }

        val realId = OtherUtil().getRealId(context.getEvent())

        val userGameInfo = lifeRestartService.selectRestartInfoByRealId(realId)
        val userInfo = LifeRestartUtil.UserInfo(
            userId = realId,
            attributes = null,
            age = -1,
            gameTimes = userGameInfo?.times ?: 0,
            achievement = userGameInfo?.cachv ?: 0,
        )

        redisService.deleteKey("lifeRestart:sendMessage:${realId}")
        redisService.setValueWithExpiry("lifeRestart:userInfo:${realId}", userInfo, 5L, TimeUnit.MINUTES)

        // 抽取天赋
        restartUtil.talentRandomInit(userInfo = userInfo)


        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStartTalent-${UUID.randomUUID()}",
            url = "http://localhost:${webImgUtil.usePort}/lifeRestartTalent?userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(context, imageData)

        context.sendMsg(RestartRespEnum.GAME_START_SUCCESS.message)
        webImgUtil.deleteImg(imageData)
    }

    @AParameter
    @Executor(action = "天赋 (.*)")
    fun getTalent(context: Context, matcher: Matcher) {
        val realId = OtherUtil().getRealId(context.getEvent())
        val userInfo = restartUtil.findUserInfo(realId)

        val errorState = errorState(userInfo, OperationType.CHOOSE_TALENT)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message)
            return
        }

        val talentInput = matcher.group(1)
        if (!isTalentFormatValid(talentInput)) {
            context.sendMsg(RestartRespEnum.TALENT_FORMAT_ERROR.message)
            return
        }
        when (restartUtil.talentCheck(talentInput, userInfo!!)) {
            TALENT_SELECT_NOT_COMPLETE -> context.sendMsg("要选满 ${userInfo.talentSelectLimit} 个不同的天赋,请重新选择")

            TALENT_SELECT_Limit -> context.sendMsg("只能选择 ${userInfo.talentSelectLimit} 个天赋,请重新选择")

            else -> {
                restartUtil.getChoiceTalent(talentInput, userInfo)

                // 发送已经激活的天赋
                val activatedTalents = restartUtil.getTalentAllocationAddition(userInfo)
                activatedTalents.forEach { context.sendMsg("天赋「${it.name}」发动 -- ${it.description}") }

                // 清空临时天赋列表并更新缓存
                userInfo.randomTalentTemp = mutableListOf()
                restartUtil.updateUserInfo(userInfo)

                context.sendMsg("请输入「分配 颜值 智力 体质 家境」或者「随机」来获取随机属性,你总共有 ${userInfo.status} 点属性可以分配")
            }
        }
    }

    @AParameter
    @Executor(action = "随机")
    fun randomAttribute(context: Context, matcher: Matcher) {
        val realId = OtherUtil().getRealId(context.getEvent())
        val userInfo = restartUtil.findUserInfo(realId)

        val errorState = errorState(userInfo, OperationType.UNALLOCATED)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message)
            return
        }

        lifeRestartService.insertTimesByRealId(realId)
        restartUtil.randomAttributes(userInfo!!)

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())
        val sendMsg = restartUtil.trajectory(userInfo, ageData, eventData)


        val sendMsgList =
            redisService.getValue("lifeRestart:sendMessage:${realId}") as? MutableList<Any?> ?: mutableListOf()
        sendMsgList.add(sendMsg)
        redisService.setValueWithExpiry("lifeRestart:sendMessage:${realId}", sendMsgList, 5L, TimeUnit.MINUTES)

        // 胎死腹中 直接结束游戏
        if (userInfo.isEnd == true) {
            sendGameEnd(context, userInfo)
            redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.SECONDS))
            return
        }

        userInfo.propertyDistribution = true
        updateAndSend(context, userInfo, realId, RestartRespEnum.CONTINUER_SUCCESS.message)
    }

    @AParameter
    @Executor(action = "分配 (.*)")
    fun dealAttribute(context: Context, matcher: Matcher) {
        val realId = OtherUtil().getRealId(context.getEvent())
        val userInfo = restartUtil.findUserInfo(realId)


        if (!Regex("^\\d+( \\d+)*\$").matches(matcher.group(1))) {
            context.sendMsg(RestartRespEnum.ASSIGN_ATTRIBUTES_ERROR.message)
            return
        }

        // 检查其他状态是否正确
        val errorState = errorState(userInfo, OperationType.UNALLOCATED)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message)
            return
        }

        // 检查分配属性是否正确
        val assignState = restartUtil.assignAttributes(userInfo!!, matcher)
        if (assignState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(assignState.message)
        }
        userInfo.propertyDistribution = true

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())
        val sendMsg = restartUtil.trajectory(userInfo, ageData, eventData)

        lifeRestartService.insertTimesByRealId(realId)

        val sendMsgList =
            redisService.getValue("lifeRestart:sendMessage:${realId}") as? MutableList<Any?> ?: mutableListOf()
        sendMsgList.add(sendMsg)
        redisService.setValueWithExpiry("lifeRestart:sendMessage:${realId}", sendMsgList, 5L, TimeUnit.MINUTES)

        if (userInfo.isEnd == true) {
            sendGameEnd(context, userInfo)
            redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.SECONDS))
            redisService.deleteKey("lifeRestart:sendMessage:${realId}")
            System.gc()
            return
        }

        updateAndSend(context, userInfo, realId, RestartRespEnum.CONTINUER_SUCCESS.message)
    }

    @AParameter
    @Executor(action = "继续(.*)")
    fun continueGame(context: Context, matcher: Matcher) {
        val realId = OtherUtil().getRealId(context.getEvent())
        val userInfo = restartUtil.findUserInfo(realId)

        // 判断错误状态
        val errorState = errorState(userInfo, OperationType.CONTINUE)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message)
            return
        }

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())

        val stepNext = matcher.group(1).trim().toIntOrNull() ?: 1 // 默认为1步

        val strList = mutableListOf<Any?>()
        for (i in 1..stepNext) {
            val sendMessage = restartUtil.trajectory(userInfo!!, ageData, eventData)
            strList.add(sendMessage)

            // 判断是否结束游戏
            if (userInfo.isEnd == true) {
                val sendMsgList =
                    redisService.getValue("lifeRestart:sendMessage:${realId}") as? MutableList<Any?>
                        ?: mutableListOf()
                sendMsgList.addAll(strList)
                redisService.setValueWithExpiry(
                    "lifeRestart:sendMessage:${realId}",
                    sendMsgList,
                    5L,
                    TimeUnit.MINUTES
                )

                sendGameEnd(context, userInfo)
                redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.SECONDS))
                redisService.deleteKey("lifeRestart:sendMessage:${realId}")
                System.gc()
                return
            }
        }

        val sendMsgList =
            redisService.getValue("lifeRestart:sendMessage:${realId}") as? MutableList<Any?> ?: mutableListOf()
        sendMsgList.addAll(strList)
        redisService.setValueWithExpiry("lifeRestart:sendMessage:${realId}", sendMsgList, 5L, TimeUnit.MINUTES)

        updateAndSend(context, userInfo!!, realId)
    }
}
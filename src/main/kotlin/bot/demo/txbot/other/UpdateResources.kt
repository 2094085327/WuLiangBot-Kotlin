package bot.demo.txbot.other

import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.genShin.util.InitGenShinData
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component


/**
 * @description: 更新所用到的资源文件，包括图片、数据等
 * @author Nature Zero
 * @date 2024/3/12 8:08
 */
@Shiro
@Component
class UpdateResources {
    companion object {
        private var owner: String = ""
        private var repoName: String = ""
        private var accessToken: String? = null
    }

    @Value("\${github.owner}")
    fun getOwner(githubOwner: String) {
        owner = githubOwner
    }

    @Value("\${github.repo}")
    fun getRepo(githubRepo: String) {
        repoName = githubRepo
    }

    @Value("\${github.access-token}")
    fun getAccessToken(githubAccessToken: String) {
        accessToken = githubAccessToken
    }

    val otherUtil = OtherUtil()

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "更新资源")
    fun updateAll(bot: Bot, event: AnyMessageEvent) {
        val folderPath = "resources"

        val downloadCheck = otherUtil.downloadFolderFromGitHub(
            owner,
            repoName,
            folderPath,
            folderPath,
            accessToken
        )

        if (!downloadCheck. first) {
            bot.sendMsg(event, "资源更新失败,自动跳过此资源更新,请通知管理员检查错误或稍后再试", false)
            return
        }
        bot.sendMsg(event, "资源更新完成，本次共更新${downloadCheck.second}个资源", false)
        OtherUtil.fileCount = 0

        // 重新初始化原神相关数据
        InitGenShinData.initGachaLogData()
    }

}
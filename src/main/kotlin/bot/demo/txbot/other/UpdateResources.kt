package bot.demo.txbot.other

import bot.demo.txbot.common.utils.OtherUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File


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
        val rootFolder = File("resources")
        val excludedFolders = listOf("imageCache")
        val allFolderPaths = otherUtil.getAllRelativePaths(rootFolder, rootFolder, excludedFolders)
        val folderPath = "resources"
        println("All folder paths:")
        if (!otherUtil.downloadFolderFromGitHub(
                owner,
                repoName,
                folderPath,
                folderPath,
                accessToken
            )
        ) {
            bot.sendMsg(event, "资源更新失败,自动跳过此资源更新,请通知管理员检查错误或稍后再试", false)
            return
        }
        bot.sendMsg(event, "资源更新完成", false)
    }

}
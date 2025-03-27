package bot.wuliang.botUtil

import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.otherUtil.OtherUtil
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import org.springframework.stereotype.Component

@Component
object GensokyoUtil {

    /**
     * 根据事件类型获取真实ID
     *
     * @return 真实ID
     */
    fun MessageEvent.getRealId(): String {
        val fictitiousId = if (this.messageType == "group") {
            this as GroupMessageEvent
            this.groupId
        } else this.userId
        return getRealIdFromServer(fictitiousId)
    }

    fun MessageEvent.getRealUserId(): String {
        return getRealIdFromServer(this.userId)
    }

    private fun getRealIdFromServer(id: Long): String {
        return HttpUtil.doGetJson("http://localhost:${OtherUtil.gskPort}/getid?type=2&id=$id")["id"].textValue()
    }
}

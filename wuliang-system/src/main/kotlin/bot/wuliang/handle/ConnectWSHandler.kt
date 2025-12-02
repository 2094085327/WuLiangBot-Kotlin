package bot.wuliang.handle

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WebSocketSessionConfig.sessionsMap
import bot.wuliang.config.WsMessageEncoder
import bot.wuliang.exception.RespBean
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.websocket.OnClose
import javax.websocket.OnOpen
import javax.websocket.Session
import javax.websocket.server.ServerEndpoint

@Component
@ServerEndpoint("/connect", encoders = [WsMessageEncoder::class])
class ConnectWSHandler {

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    /**
     * 建立连接成功调用
     */
    @OnOpen
    fun onOpen(session: Session) {
        sessionsMap[session.id] = session

        // 发送初始连接成功消息
        sendObject(session, RespBean.success())

        // 启动心跳检测
        startHeartbeat(session)
    }

    /**
     * 启动心跳检测
     */
    private fun startHeartbeat(session: Session) {
        scheduler.scheduleAtFixedRate({
            try {
                if (session.isOpen) {
                    // 发送心跳消息
                    sendObject(session, RespBean.success("heartbeat"))
                } else {
                    // 连接已关闭，清理资源
                    sessionsMap.remove(session.id)
                }
            } catch (e: Exception) {
                logInfo("Session:${session.id} - 心跳检测失败: ${e.message}")
                sessionsMap.remove(session.id)
            }
        }, 30, 30, TimeUnit.SECONDS) // 每30秒发送一次心跳
    }


    /**
     * 关闭连接调用
     */
    @OnClose
    fun onClose(session: Session) {
        sessionsMap.remove(session.id)
        logInfo("Session:${session.id} - 连接已关闭")
    }

    fun sendObject(session: Session, message: Any) {
        session.basicRemote.sendObject(message)
    }
}
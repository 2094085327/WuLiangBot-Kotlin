package bot.wuliang.handle

import bot.wuliang.config.WebSocketSessionConfig
import bot.wuliang.exception.RespBean
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class ConnectWSHandler(private val sessions: WebSocketSessionConfig) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        if (sessions.register(session, RespBean.success("heartbeat"))) {
            sessions.send(session.id, RespBean.success<Nothing>())
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) = sessions.close(session.id)

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) =
        sessions.close(session.id, CloseStatus.SERVER_ERROR)

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) =
        sessions.close(session.id, CloseStatus.NOT_ACCEPTABLE)
}
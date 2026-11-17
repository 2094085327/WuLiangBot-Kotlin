package bot.wuliang.config

import bot.wuliang.handle.ConnectWSHandler
import bot.wuliang.handle.LogsWSHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val logs: LogsWSHandler,
    private val connect: ConnectWSHandler,
    private val auth: ManagementSessionAuth,
    @Value("\${management.websocket.allowed-origins:http://localhost:16666,http://127.0.0.1:16666,http://wuliang.asia:16666}")
    private val allowedOrigins: String,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val authentication = object : HandshakeInterceptor {
            override fun beforeHandshake(
                request: ServerHttpRequest,
                response: ServerHttpResponse,
                handler: WebSocketHandler,
                attributes: MutableMap<String, Any>,
            ): Boolean {
                val servlet = (request as? ServletServerHttpRequest)?.servletRequest
                val ticket = servlet?.let(auth::ticket)
                if (!auth.isValid(ticket)) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED)
                    return false
                }
                attributes[ManagementSessionAuth.TICKET_ATTRIBUTE] = ticket!!
                return true
            }

            override fun afterHandshake(
                request: ServerHttpRequest,
                response: ServerHttpResponse,
                handler: WebSocketHandler,
                exception: Exception?,
            ) = Unit
        }
        val origins = allowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()
        registry.addHandler(logs, "/logs").addInterceptors(authentication).setAllowedOrigins(*origins)
        registry.addHandler(connect, "/connect").addInterceptors(authentication).setAllowedOrigins(*origins)
    }
}
package bot.wuliang.config

import com.fasterxml.jackson.databind.ObjectMapper
import javax.websocket.Encoder
import javax.websocket.EndpointConfig

class WsMessageEncoder : Encoder.Text<Any> {
    private val objectMapper = ObjectMapper()

    override fun init(config: EndpointConfig?) {}

    override fun destroy() {}

    override fun encode(message: Any): String {
        return objectMapper.writeValueAsString(message)
    }
}
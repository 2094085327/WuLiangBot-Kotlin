package bot.wuliang.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.websocket.RemoteEndpoint

/** 两种管理连接共用有界发送队列和维护线程，不为连接创建线程。 */
@Component
class WebSocketSessionConfig(
    private val auth: ManagementSessionAuth,
    private val objectMapper: ObjectMapper,
) {
    private data class Packet(val text: String, val bytes: Int)

    private class Client(
        val session: WebSocketSession,
        val remote: RemoteEndpoint.Async,
        val ticket: String,
        val heartbeat: Packet,
        val onClosed: () -> Unit,
    ) {
        val queue = ArrayDeque<Packet>()
        var pendingBytes = 0
        var sending = false
        var sendingSince = 0L
        var closed = false
    }

    private val clients = ConcurrentHashMap<String, Client>()
    private val admissionLock = Any()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "management-ws-maintenance").apply { isDaemon = true }
    }
    @Volatile
    private var stopped = false

    @PostConstruct
    fun start() {
        scheduler.scheduleWithFixedDelay({ runCatching { maintain() } }, 30, 30, TimeUnit.SECONDS)
    }

    fun register(session: WebSocketSession, heartbeat: Any, onClosed: () -> Unit = {}): Boolean {
        val ticket = session.attributes[ManagementSessionAuth.TICKET_ATTRIBUTE] as? String
        val native = (session as? StandardWebSocketSession)?.nativeSession
        if (ticket == null || native == null) {
            runCatching { session.close(CloseStatus.POLICY_VIOLATION) }
            return false
        }
        val client = Client(session, native.asyncRemote, ticket, encode(heartbeat), onClosed)
        client.remote.sendTimeout = SEND_TIMEOUT_MS
        session.textMessageSizeLimit = 1024
        session.binaryMessageSizeLimit = 1024
        val accepted = synchronized(admissionLock) {
            if (stopped || clients.size >= MAX_CONNECTIONS) false
            else {
                clients[session.id] = client; true
            }
        }
        if (!accepted) runCatching { session.close(CloseStatus(1013, "管理连接已满")) }
        return accepted
    }

    fun contains(id: String): Boolean = clients.containsKey(id)

    fun send(id: String, value: Any) = enqueue(id, encode(value))

    fun sendAll(ids: Collection<String>, value: Any) {
        if (ids.isEmpty()) return
        val packet = encode(value)
        ids.forEach { enqueue(it, packet) }
    }

    private fun enqueue(id: String, packet: Packet) {
        val client = clients[id] ?: return
        var first: Packet? = null
        val accepted = synchronized(client) {
            if (client.closed) return
            if (client.queue.size >= MAX_QUEUED_MESSAGES || client.pendingBytes + packet.bytes > MAX_QUEUED_BYTES) {
                false
            } else {
                client.queue.addLast(packet)
                client.pendingBytes += packet.bytes
                if (!client.sending) {
                    client.sending = true
                    client.sendingSince = System.nanoTime()
                    first = client.queue.removeFirst()
                }
                true
            }
        }
        if (!accepted) close(id, CloseStatus(1013, "日志接收过慢，请重新连接"))
        else first?.let { transmit(client, it) }
    }

    private fun encode(value: Any): Packet {
        val text = objectMapper.writeValueAsString(value)
        return Packet(text, text.toByteArray(Charsets.UTF_8).size)
    }

    private fun transmit(client: Client, packet: Packet) {
        try {
            client.remote.sendText(packet.text) { result ->
                if (!result.isOK) {
                    close(client.session.id, CloseStatus.SERVER_ERROR)
                    return@sendText
                }
                var next: Packet? = null
                synchronized(client) {
                    if (client.closed) return@sendText
                    client.pendingBytes -= packet.bytes
                    if (client.queue.isEmpty()) client.sending = false
                    else {
                        next = client.queue.removeFirst()
                        client.sendingSince = System.nanoTime()
                    }
                }
                next?.let { transmit(client, it) }
            }
        } catch (_: Exception) {
            close(client.session.id, CloseStatus.SERVER_ERROR)
        }
    }

    fun close(id: String, status: CloseStatus = CloseStatus.NORMAL) {
        val client = clients.remove(id) ?: return
        synchronized(client) {
            client.closed = true
            client.queue.clear()
            client.pendingBytes = 0
        }
        runCatching { client.onClosed() }
        runCatching { if (client.session.isOpen) client.session.close(status) }
    }

    private fun maintain() {
        val validTickets = HashMap<String, Boolean>()
        for ((id, client) in clients) {
            val valid = validTickets.getOrPut(client.ticket) { auth.isValid(client.ticket) }
            when {
                !valid -> close(id, CloseStatus(1008, "登录已过期"))
                !client.session.isOpen -> close(id)
                synchronized(client) {
                    client.sending && System.nanoTime() - client.sendingSince > TimeUnit.MILLISECONDS.toNanos(
                        SEND_TIMEOUT_MS
                    )
                } -> close(id, CloseStatus(1013, "日志接收超时"))

                else -> enqueue(id, client.heartbeat)
            }
        }
    }

    @PreDestroy
    fun stop() {
        synchronized(admissionLock) { stopped = true }
        scheduler.shutdownNow()
        clients.keys.toList().forEach { close(it, CloseStatus.GOING_AWAY) }
    }

    companion object {
        private const val MAX_CONNECTIONS = 8
        private const val MAX_QUEUED_MESSAGES = 16
        private const val MAX_QUEUED_BYTES = 512 * 1024
        private const val SEND_TIMEOUT_MS = 5000L
    }
}
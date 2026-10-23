package bot.wuliang.arbitration

import java.io.BufferedReader

/** 连续整点排期：只保存首个时间、去重节点表及每小时的节点下标。 */
class ArbitrationSchedule private constructor(
    private val activation: Long,
    private val nodes: Array<String>,
    private val nodeIndices: IntArray,
) {
    val size: Int get() = nodeIndices.size
    val expiry: Long get() = activation + size.toLong() * HOUR_MILLIS

    fun covers(now: Long): Boolean = now in activation..<expiry

    /** 利用连续小时直接定位当前项，无需扫描或复制剩余排期。 */
    fun currentIndex(now: Long): Int = if (covers(now)) ((now - activation) / HOUR_MILLIS).toInt() else -1

    fun nodeIdAt(index: Int): String = nodes[nodeIndices[index]]

    fun activationAt(index: Int): Long {
        require(index in nodeIndices.indices) { "仲裁排期下标越界" }
        return activation + index.toLong() * HOUR_MILLIS
    }

    fun expiryAt(index: Int): Long = activationAt(index) + HOUR_MILLIS

    companion object {
        private const val HOUR_MILLIS = 3_600_000L
        private val NODE_PATTERN = Regex("(?:Sol|Clan|Settlement)Node[0-9]+")

        /** 流式校验全部记录，只保留当前及未来排期。 */
        fun read(reader: BufferedReader, now: Long): ArbitrationSchedule {
            val dictionary = LinkedHashMap<String, Int>()
            var indices = IntArray(1024)
            var count = 0
            var firstActivation = 0L
            var previousSeconds = -1L
            var firstLine = true
            while (true) {
                val raw = reader.readLine() ?: break
                val line = (if (firstLine) raw.removePrefix("\uFEFF") else raw).trim()
                firstLine = false
                if (line.isBlank()) continue
                val comma = line.indexOf(',')
                require(comma > 0 && comma == line.lastIndexOf(',')) { "仲裁排期格式无效" }
                val seconds = requireNotNull(line.substring(0, comma).trim().toLongOrNull()) { "仲裁时间戳无效" }
                val nodeId = line.substring(comma + 1).trim()
                require(NODE_PATTERN.matches(nodeId)) { "仲裁节点格式无效" }
                require(seconds in 0..253402210800L && seconds % 3600 == 0L) { "仲裁时间需为整点" }
                require(previousSeconds < 0 || seconds == previousSeconds + 3600) { "仲裁排期小时不连续" }
                previousSeconds = seconds

                val start = seconds * 1000
                if (start + HOUR_MILLIS <= now) continue
                if (count == 0) firstActivation = start
                if (count == indices.size) indices = indices.copyOf(indices.size * 2)
                indices[count++] = dictionary.getOrPut(nodeId) { dictionary.size }
            }
            require(previousSeconds >= 0) { "仲裁排期为空" }
            require(count > 0) { "仲裁排期已到期" }
            return ArbitrationSchedule(firstActivation, dictionary.keys.toTypedArray(), indices.copyOf(count))
        }
    }
}
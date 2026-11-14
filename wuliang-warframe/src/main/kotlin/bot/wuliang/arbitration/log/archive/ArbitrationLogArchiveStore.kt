package bot.wuliang.arbitration.log.archive

import bot.wuliang.arbitration.log.ArbitrationLogParseResult
import bot.wuliang.mapper.ArbitrationLogArchiveMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArbitrationLogArchiveStore(
    private val mapper: ArbitrationLogArchiveMapper,
    private val codec: ArbitrationLogArchiveCodec
) {
    /** 一份文件最多20局 */
    @Transactional(rollbackFor = [Exception::class], timeout = 15)
    fun record(parsed: ArbitrationLogParseResult, fileDigest: String, uploader: ArbitrationLogUploader): Boolean {
        require(parsed.runs.size in 1..20)
        val senderKey = uploader.key()
        val records = parsed.runs.map { codec.encode(it, parsed.warnings, fileDigest, parsed.inputIncomplete) }
        val manifest = codec.manifest(parsed, records)
        val now = System.currentTimeMillis()
        var conflict = false
        for (record in records.sortedBy { it.runKey }) {
            mapper.insertIfAbsent(record, now)
            val previous = checkNotNull(mapper.state(record.runKey))
            val previousRank = previous.detailRank
            check(previousRank == 1 || previousRank == 2) { "仲裁归档详情状态无效。" }
            if (previous.factsHash != record.factsHash) {
                if (!previous.completed && (record.completed || record.observedSeconds > previous.observedSeconds)) {
                    mapper.replaceIncomplete(record, now)
                } else if (previous.completed && record.completed) {
                    mapper.markConflict(record.runKey, now)
                    conflict = true
                }
            } else if (record.detailRank > previousRank) {
                mapper.enrich(record, now)
            }
            mapper.associate(record.runKey, senderKey, uploader, now)
        }
        mapper.remember(senderKey, fileDigest, manifest, now)
        return conflict
    }

    @Transactional(readOnly = true, timeout = 5)
    fun byRecordId(recordId: Long, uploader: ArbitrationLogUploader): ArbitrationLogParseResult {
        require(recordId > 0) { "记录编号需为有效的正整数。" }
        val payload = mapper.recordPayload(recordId, uploader.key())
        require(payload != null) { "没有找到这条仲裁记录，请使用「我的仲裁」确认编号。" }
        return codec.decodeRecord(payload)
    }

    @Transactional(readOnly = true, timeout = 5)
    fun previous(uploader: ArbitrationLogUploader, run: Int): ArbitrationLogParseResult {
        val senderKey = uploader.key()
        val file = mapper.lastFile(senderKey)
        require(file != null) { "还没有可复用的日志记录，请先发送 EE.log 一次。" }
        val manifest = codec.readManifest(file.manifest)
        require(run in 1..manifest.runs.size) { "上次的日志可选择最近${manifest.runs.size}局。第1局是最近一局。" }
        val reference = manifest.runs[run - 1]
        val payload = mapper.payload(reference.runKey, senderKey)
        require(payload != null) { "上次的记录不可用，请重新发送 EE.log。" }
        val selected = codec.decode(payload, reference, file.fileDigest)
        return ArbitrationLogParseResult(
            listOf(selected), manifest.validTotal, manifest.excludedShort,
            manifest.excludedNonHost, manifest.warnings, manifest.inputIncomplete
        )
    }
}
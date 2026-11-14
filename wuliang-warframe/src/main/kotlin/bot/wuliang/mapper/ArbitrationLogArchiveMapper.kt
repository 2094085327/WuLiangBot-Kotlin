package bot.wuliang.mapper

import bot.wuliang.arbitration.log.archive.*
import bot.wuliang.arbitration.log.history.ArbitrationLogHistoryRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface ArbitrationLogArchiveMapper {
    fun insertIfAbsent(@Param("r") record: ArbitrationArchivedRun, @Param("now") now: Long)
    fun state(@Param("key") key: String): ArbitrationArchiveState?
    fun replaceIncomplete(@Param("r") record: ArbitrationArchivedRun, @Param("now") now: Long)
    fun enrich(@Param("r") record: ArbitrationArchivedRun, @Param("now") now: Long)
    fun markConflict(@Param("key") key: String, @Param("now") now: Long)
    fun associate(
        @Param("key") key: String, @Param("senderKey") senderKey: String,
        @Param("u") uploader: ArbitrationLogUploader, @Param("now") now: Long
    )

    fun remember(
        @Param("senderKey") senderKey: String, @Param("fileDigest") fileDigest: String,
        @Param("manifest") manifest: String, @Param("now") now: Long
    )

    fun lastFile(@Param("senderKey") senderKey: String): ArbitrationLastFileState?
    fun payload(@Param("key") key: String, @Param("senderKey") senderKey: String): ArbitrationArchivePayload?
    fun recordPayload(@Param("recordId") recordId: Long, @Param("senderKey") senderKey: String): ArbitrationArchivePayload?
    fun historyCount(@Param("senderKey") senderKey: String): Long
    fun historyPage(@Param("senderKey") senderKey: String, @Param("offset") offset: Long,
                    @Param("limit") limit: Int): List<ArbitrationLogHistoryRecord>
}
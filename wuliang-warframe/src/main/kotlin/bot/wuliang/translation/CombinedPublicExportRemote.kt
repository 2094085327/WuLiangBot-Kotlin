package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import java.nio.file.Path

/** 来源提交分别固定；一个来源失败时不返回候选版本，存储层继续保留旧快照。 */
internal class CombinedPublicExportRemote(
    private val plus: PublicExportRemote,
    private val languages: PublicExportRemote,
    private val official: PublicExportRemote,
) : FilePublicExportRemote {
    override fun revision(): ExportRevision {
        val primary = official.revision()
        val export = plus.revision().also(PublicExportStore::validateRevision)
        val language = languages.revision()
        require(language.files.map { it.path }.toSet() == PublicExportPolicy.LOCALES.map { "$it.json" }
            .toSet() && language.files.size == PublicExportPolicy.LOCALES.size) {
            "完整语言表缺少中文或英文文件"
        }
        val sources = primary.sources + mapOf("plus" to export.commit, "languages" to language.commit)
        val versionKey = "${PublicExportPolicy.SNAPSHOT_VERSION}:${primary.commit}:${export.commit}:${language.commit}"
        val version = PublicExportStore.gitBlobSha(versionKey.toByteArray(Charsets.UTF_8))
        val files = export.files.map { it.copy(sourceCommit = export.commit) } + language.files.map {
            it.copy(path = "languages.${it.path}", sourceCommit = language.commit)
        } + primary.files
        return ExportRevision(version, files.sortedBy { it.path }, sources).also(PublicExportStore::validateRevision)
    }

    override fun download(commit: String, file: ExportFile): ByteArray {
        if (PublicExportStore.acceptsOfficial(file.path)) return official.download(commit, file)
        val sourceCommit = requireNotNull(file.sourceCommit) { "合并快照文件缺少来源提交" }
        return if (file.path in PublicExportStore.languageFiles) {
            languages.download(sourceCommit, file.copy(path = file.path.removePrefix("languages.")))
        } else plus.download(sourceCommit, file)
    }

    override fun downloadTo(commit: String, file: ExportFile, target: Path) {
        if (PublicExportStore.acceptsOfficial(file.path)) {
            official.downloadTo(commit, file, target)
            return
        }
        val sourceCommit = requireNotNull(file.sourceCommit) { "合并快照文件缺少来源提交" }
        if (file.path in PublicExportStore.languageFiles) {
            languages.downloadTo(sourceCommit, file.copy(path = file.path.removePrefix("languages.")), target)
        } else plus.downloadTo(sourceCommit, file, target)
    }
}
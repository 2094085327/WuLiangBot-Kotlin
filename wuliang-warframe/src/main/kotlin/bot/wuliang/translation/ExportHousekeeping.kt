package bot.wuliang.translation

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.config.PublicExportPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal data class ExportCleanupResult(
    var snapshots: Int = 0,
    var indexes: Int = 0,
    var cacheFiles: Int = 0,
    var temporaryFiles: Int = 0,
    var bytes: Long = 0,
)

/** 处理词库目录内的已知平面文件 */
internal class ExportHousekeeping(
    private val root: Path,
    private val aliases: Map<String, String>,
    private val mapper: ObjectMapper,
) {
    private data class Snapshot(val directory: Path, val manifest: ExportManifest?, val modified: Long)

    /** 加载前清理旧指纹索引及过期索引临时文件；正在使用时保留全部文件。 */
    fun pruneIndexes(directory: Path, manifest: ExportManifest) {
        val base = root.toRealPath()
        val snapshots = base.resolve("snapshots")
        val target = directory.toRealPath()
        if (!plainDirectory(snapshots, base) || !plainDirectory(target, snapshots)) return
        val expected = expectedIndexes(manifest)
        val result = ExportCleanupResult()
        val now = System.currentTimeMillis()
        safely(target) {
            SnapshotUsage.exclusive(target) {
                files(target).filter { unusedIndexOrTemporary(it, expected, now) }
                    .take(MAX_FILES)
                    .forEach { path ->
                        remove(path, target, result, false) {
                            if (INDEX.matches(path.fileName.toString())) result.indexes++ else result.temporaryFiles++
                        }
                    }
            }
        }
        if (result.indexes + result.temporaryFiles > 0) logInfo(
            "词库加载前清理：旧索引 {}，临时文件 {}，文件大小合计 {} KiB",
            result.indexes, result.temporaryFiles, result.bytes / 1024,
        )
    }

    /** 调用期间必须持有 sync.lock。dryRun 仅计算清理结果，不删除文件。 */
    fun clean(current: Path, dryRun: Boolean = false, now: Long = System.currentTimeMillis()): ExportCleanupResult {
        val result = ExportCleanupResult()
        val base = root.toRealPath()
        val snapshots = base.resolve("snapshots")
        val selected = current.toRealPath()
        if (!plainDirectory(snapshots, base) || !plainDirectory(selected, snapshots)) return result
        val entries = Files.newDirectoryStream(snapshots).use { stream ->
            stream.filter {
                PublicExportPolicy.SNAPSHOT_DIRECTORY_PATTERN.matches(it.fileName.toString()) && plainDirectory(
                    it,
                    snapshots
                )
            }
                .map(::snapshot).toList()
        }
        if (entries.none { it.directory == selected && it.manifest != null }) return result
        // 只有当前指针已经通过加载的快照，以及最近一份完整历史快照，属于固定保留项。
        val previous = entries.filter { it.directory != selected && it.manifest != null }
            .maxWithOrNull(compareBy<Snapshot> { it.modified }.thenBy { it.directory.fileName.toString() })
        val keep = setOfNotNull(selected, previous?.directory)
        val cacheAssets = HashSet<String>()
        var deletions = 0
        entries.sortedByDescending { it.modified }.forEach { entry ->
            var preserved = true
            safely(entry.directory) {
                val children = files(entry.directory)
                val declared = entry.manifest?.files?.mapTo(HashSet()) { it.path }
                if (children.any {
                        !knownSnapshotFile(it.fileName.toString(), declared) || !plainFile(
                            it,
                            entry.directory
                        )
                    }) return@safely
                val snapshotFiles = children.filter { it.fileName.toString() != SnapshotUsage.LOCK_FILE }
                if (entry.directory in keep) {
                    val expected = entry.manifest?.let(::expectedIndexes).orEmpty()
                    SnapshotUsage.exclusive(entry.directory) {
                        children.filter { unusedIndexOrTemporary(it, expected, now) }.forEach { path ->
                            if (deletions < MAX_FILES && remove(path, entry.directory, result, dryRun) {
                                    if (INDEX.matches(path.fileName.toString())) result.indexes++ else result.temporaryFiles++
                                }) deletions++
                        }
                    }
                } else if ((entry.manifest != null || now - entry.modified >= TEMP_AGE) && deletions + snapshotFiles.size + 2 <= MAX_FILES) {
                    val removed = SnapshotUsage.exclusive(entry.directory) {
                        snapshotFiles.forEach {
                            remove(it, entry.directory, result, dryRun) { }
                        }
                        true
                    } == true
                    if (removed) {
                        remove(entry.directory.resolve(SnapshotUsage.LOCK_FILE), entry.directory, result, dryRun) { }
                        if (dryRun || Files.deleteIfExists(entry.directory)) {
                            result.snapshots++
                            deletions += snapshotFiles.size + 2
                            preserved = false
                        }
                    }
                }
            }
            if (preserved) entry.manifest?.files?.filter { PublicExportStore.acceptsOfficial(it.path) }
                ?.mapNotNullTo(cacheAssets) { it.sourceCommit }
        }
        val cache = base.resolve(PublicExportPolicy.OFFICIAL_CACHE_DIRECTORY)
        if (plainDirectory(cache, base)) safely(cache) {
            Files.newDirectoryStream(cache).use { stream ->
                for (path in stream) {
                    if (deletions >= MAX_FILES) break
                    val name = path.fileName.toString()
                    val asset = OFFICIAL_CACHE.matchEntire(name)?.groupValues?.get(1)
                    if (plainFile(path, cache) &&
                        ((asset != null && asset !in cacheAssets && stale(path, now, CACHE_AGE)) ||
                                (OFFICIAL_TEMP.matches(name) && stale(path, now, TEMP_AGE)))
                    ) {
                        if (remove(path, cache, result, dryRun) {
                                if (asset != null) result.cacheFiles++ else result.temporaryFiles++
                            }) deletions++
                    }
                }
            }
        }
        safely(base) {
            files(base).filter { POINTER_TEMP.matches(it.fileName.toString()) && stale(it, now, TEMP_AGE) }.forEach {
                if (deletions < MAX_FILES && remove(it, base, result, dryRun) { result.temporaryFiles++ }) deletions++
            }
        }
        return result
    }

    private fun snapshot(directory: Path): Snapshot {
        val file = directory.resolve("manifest.json")
        val manifest = recoverExportData {
            require(plainFile(file, directory) && Files.size(file) <= PublicExportPolicy.MAX_INDEX_BYTES)
            mapper.readValue<ExportManifest>(Files.readAllBytes(file)).also { value ->
                PublicExportStore.validateRevision(ExportRevision(value.appliedCommit, value.files, value.sources))
                require(directory.fileName.toString().startsWith("${value.appliedCommit}-"))
                require(value.files.all {
                    plainFile(
                        directory.resolve(it.path),
                        directory
                    ) && Files.size(directory.resolve(it.path)) == it.size
                })
            }
        }.getOrNull()
        return Snapshot(
            directory,
            manifest,
            Files.getLastModifiedTime(if (manifest == null) directory else file, NOFOLLOW_LINKS).toMillis()
        )
    }

    private fun expectedIndexes(manifest: ExportManifest): Set<String> {
        val fingerprint = PublicExportIndex.nameIndexFingerprint(manifest.files, aliases)
        return PublicExportPolicy.LOCALES.mapTo(HashSet()) { "names-$it-$fingerprint.bin" }
    }

    private fun knownSnapshotFile(name: String, declared: Set<String>?): Boolean =
        name == "manifest.json" || name == SnapshotUsage.LOCK_FILE || INDEX.matches(name) || TEMP.matches(name) ||
                (if (declared == null) PublicExportStore.accepts(name) else name in declared)

    private fun files(directory: Path): List<Path> = Files.newDirectoryStream(directory).use { it.toList() }

    private fun unusedIndexOrTemporary(path: Path, expected: Set<String>, now: Long): Boolean {
        val name = path.fileName.toString()
        return (INDEX.matches(name) && name !in expected) || (TEMP.matches(name) && stale(path, now, TEMP_AGE))
    }

    private fun plainDirectory(path: Path, parent: Path): Boolean = path.parent == parent &&
            Files.isDirectory(path, NOFOLLOW_LINKS) && path.toRealPath() == path

    private fun plainFile(path: Path, parent: Path): Boolean = path.parent == parent &&
            Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath() == path

    private fun stale(path: Path, now: Long, age: Long): Boolean =
        now - Files.getLastModifiedTime(path, NOFOLLOW_LINKS).toMillis() >= age

    private inline fun remove(
        path: Path,
        parent: Path,
        result: ExportCleanupResult,
        dryRun: Boolean,
        count: () -> Unit
    ): Boolean {
        require(plainFile(path, parent)) { "拒绝清理目录外路径或链接：$path" }
        val bytes = Files.size(path)
        if (dryRun || Files.deleteIfExists(path)) {
            result.bytes += bytes
            count()
            return true
        }
        return false
    }

    private inline fun safely(path: Path, action: () -> Unit) {
        try {
            action()
        } catch (error: IOException) {
            logWarn("词库清理跳过不可访问路径：{}，{}", path, error.message)
        } catch (error: IllegalArgumentException) {
            logWarn("词库清理跳过不安全路径：{}，{}", path, error.message)
        }
    }

    companion object {
        private const val MAX_FILES = 1024
        private val CACHE_AGE = TimeUnit.DAYS.toMillis(7)
        private val TEMP_AGE = TimeUnit.DAYS.toMillis(1)
        private val INDEX = Regex("names-(en|zh)-[0-9a-f]{40}\\.bin")
        private val TEMP = Regex("names-(en|zh)-[0-9a-f]{40}\\.bin-[0-9a-f-]{36}\\.tmp")
        private val OFFICIAL_CACHE = Regex("([0-9a-f]{40})\\.(json|sha)")
        private val OFFICIAL_TEMP = Regex("[0-9a-f]{40}-[0-9a-f-]{36}\\.(tmp|sha\\.tmp)")
        private val POINTER_TEMP = Regex("current-[0-9a-f-]{36}\\.tmp")
    }
}
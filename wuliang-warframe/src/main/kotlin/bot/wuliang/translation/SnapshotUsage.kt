package bot.wuliang.translation

import java.io.IOException
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/** 索引存活期间持有快照共享锁；引用队列在加载或清理时回收 */
internal object SnapshotUsage {
    const val LOCK_FILE = "in-use.lock"
    private val queue = ReferenceQueue<Any>()
    private val owners = HashMap<PhantomReference<Any>, Path>()
    private val leases = HashMap<Path, Lease>()

    private class Lease(val channel: FileChannel, val lock: FileLock, var owners: Int)

    @Synchronized
    fun retain(owner: Any, directory: Path) {
        drain()
        val path = directory.toRealPath()
        val lease = leases[path] ?: run {
            val channel = open(path)
            try {
                val lock = channel.tryLock(0, Long.MAX_VALUE, true)
                    ?: throw IOException("词库快照正在清理，请稍后重试")
                Lease(channel, lock, 0).also { leases[path] = it }
            } catch (error: Throwable) {
                channel.close()
                throw error
            }
        }
        lease.owners++
        owners[PhantomReference(owner, queue)] = path
    }

    /** 返回 null 表示仍有本机或其他进程在使用；调用方还必须持有根目录同步锁。 */
    @Synchronized
    fun <T> exclusive(directory: Path, action: () -> T): T? {
        drain()
        val path = directory.toRealPath()
        // 某些系统关闭同文件的其他句柄会释放本进程的锁，因此先检查本机登记。
        if (path in leases) return null
        return open(path).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } ?: return@use null
            lock.use { action() }
        }
    }

    private fun open(directory: Path): FileChannel {
        val file = directory.resolve(LOCK_FILE)
        require(
            !Files.exists(file, NOFOLLOW_LINKS) ||
                    (Files.isRegularFile(file, NOFOLLOW_LINKS) && file.toRealPath() == file)
        ) { "快照使用锁路径无效" }
        return FileChannel.open(file, CREATE, READ, WRITE, NOFOLLOW_LINKS)
    }

    private fun drain() {
        while (true) {
            val reference = queue.poll() ?: return
            val path = owners.remove(reference) ?: continue
            reference.clear()
            val lease = leases.getValue(path)
            if (--lease.owners == 0) {
                leases.remove(path)
                try {
                    lease.lock.release()
                } catch (_: IOException) {
                    // 继续关闭通道；未释放的操作系统锁仍会阻止清理。
                } finally {
                    lease.channel.close()
                }
            }
        }
    }
}
package bot.wuliang.restart

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.CommonConfig.FILE_CACHE_PATH
import bot.wuliang.config.CommonConfig.RESTART_CONFIG
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.system.ApplicationHome
import org.springframework.stereotype.Component
import java.io.File
import java.io.ByteArrayInputStream
import java.lang.management.ManagementFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.annotation.PostConstruct
import kotlin.system.exitProcess

@Component
class RestartManager(private val arguments: ApplicationArguments) {
    private val mapper = ObjectMapper()
    private val lock = Any()
    private val workingDirectory = File(System.getProperty("user.dir")).canonicalFile
    private val configFile = File(workingDirectory, RESTART_CONFIG)
    private val runningJar = ApplicationHome(RestartManager::class.java).source
        ?.takeIf { it.isFile && it.extension.equals("jar", ignoreCase = true) }?.canonicalFile
    private val instanceId = "${ProcessHandle.current().pid()}-${ManagementFactory.getRuntimeMXBean().startTime}"
    private var targetJar: File? = null

    data class Status(val instanceId: String, val jarFile: String?, val restarting: Boolean, val targetJar: String?)

    @PostConstruct
    fun initialize() {
        try {
            configuration()
        } catch (e: Exception) {
            // 重启配置损坏不阻止机器人启动，也不覆盖用户已经保存的内容。
            logError("重启配置初始化失败：{}", e.message)
        }
    }

    fun currentJarName(): String = runningJar?.path ?: "非Jar下运行"

    fun status(): Status = synchronized(lock) {
        Status(instanceId, runningJar?.path, targetJar != null, targetJar?.path)
    }

    fun configuration(): ObjectNode = synchronized(lock) {
        if (!configFile.exists()) {
            writeConfiguration(mapper.createObjectNode().put("jar_file", runningJar?.path.orEmpty()))
        }
        val config = mapper.readTree(configFile)
        require(config is ObjectNode && config.path("jar_file").isTextual) {
            "重启配置格式错误，请检查 $RESTART_CONFIG 中的 jar_file"
        }
        config
    }

    fun selectJar(name: String) = synchronized(lock) {
        check(targetJar == null) { "正在重启，暂时不能更换 Jar" }
        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\')) { "请选择运行目录中的 Jar 文件" }
        val file = resolveJar(name)
        validateJar(file)
        writeConfiguration(configuration().put("jar_file", file.name))
    }

    private fun resolveJar(path: String): File {
        require(path.isNotBlank()) { "请先选择要启动的 Jar 文件" }
        val file = File(path).let { if (it.isAbsolute) it else File(workingDirectory, path) }.canonicalFile
        require(file.parentFile == workingDirectory || file == runningJar) { "只能使用运行目录中的 Jar 文件" }
        require(file.isFile && file.canRead() && file.extension.equals("jar", ignoreCase = true)) {
            "Jar 文件不存在或不可读：${file.name}"
        }
        return file
    }

    private fun validateJar(file: File) {
        JarFile(file).use { jar ->
            val entry = jar.getJarEntry(JarFile.MANIFEST_NAME)
            require(entry != null && entry.size in 1..65536L) { "Jar 清单缺失或超过64KB：${file.name}" }
            val bytes = jar.getInputStream(entry).use { it.readNBytes(65537) }
            require(bytes.size <= 65536) { "Jar 清单超过64KB：${file.name}" }
            val mainClass = Manifest(ByteArrayInputStream(bytes)).mainAttributes.getValue("Main-Class")
            require(!mainClass.isNullOrBlank() && jar.getJarEntry(mainClass.replace('.', '/') + ".class") != null) {
                "Jar 缺少可执行入口：${file.name}"
            }
        }
    }

    fun checkUploadTarget(name: String) = synchronized(lock) {
        uploadDestination(name)
        Unit
    }

    private fun uploadDestination(name: String): Path {
        check(targetJar == null) { "正在重启，暂时不能上传 Jar" }
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 240 &&
                name.endsWith(".jar", ignoreCase = true) &&
                name.none { it.isISOControl() || it in "<>:\"/\\|?*" } &&
                !Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])\\..*").matches(name)) {
            "Jar 文件名无效，请使用不含目录和特殊字符的 .jar 文件名"
        }
        return File(workingDirectory, name).toPath()
    }

    fun publishUploadedJar(staging: Path, name: String): String = synchronized(lock) {
        val requested = uploadDestination(name)
        require(staging.parent == workingDirectory.toPath() && Files.isRegularFile(staging, NOFOLLOW_LINKS)) {
            "Jar 临时文件无效"
        }
        validateJar(staging.toFile())
        val baseName = name.dropLast(4)
        val extension = name.takeLast(4)
        for (suffix in 0..Int.MAX_VALUE) {
            val destination = if (suffix == 0) requested else requested.resolveSibling("$baseName-$suffix$extension")
            if (destination.toFile() == runningJar || Files.exists(destination, NOFOLLOW_LINKS)) continue
            try {
                // 原子创建完整文件的硬链接。即使外部程序抢先写入同名文件，也不会覆盖。
                // 临时文件由调用方在 finally 中移除，最终 Jar 的内容不受影响。
                Files.createLink(destination, staging)
                return@synchronized destination.fileName.toString()
            } catch (_: FileAlreadyExistsException) {
                // 目标在检查后出现，继续选择下一个后缀。
            }
        }
        throw IllegalStateException("同名 Jar 文件过多，请更换文件名")
    }

    private fun writeConfiguration(config: ObjectNode) {
        Files.createDirectories(configFile.toPath().parent)
        val temporary = Files.createTempFile(configFile.toPath().parent, "restart-config-", ".tmp")
        try {
            mapper.writeValue(temporary.toFile(), config)
            try {
                Files.move(temporary, configFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, configFile.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun requestRestart(): Status = synchronized(lock) {
        check(targetJar == null) { "重启已经提交，请等待完成" }
        check(runningJar != null) { "当前不是 Jar 运行模式，无法自动重启" }
        val selected = resolveJar(configuration().path("jar_file").asText())
        validateJar(selected)
        val java = File(System.getProperty("java.home"), if (File.separatorChar == '\\') "bin/java.exe" else "bin/java")
        check(java.isFile && java.canExecute()) { "找不到可执行的 Java 程序" }
        val command = listOf(java.absolutePath) + ManagementFactory.getRuntimeMXBean().inputArguments +
                listOf("-jar", selected.path) + arguments.sourceArgs.toList()
        val helper = startLauncher(java, command)
        try {
            // 辅助进程确认就绪后才允许退出，避免交接失败直接停服。
            helper.outputStream.use { it.write(1) }
            targetJar = selected
            Thread({
                Thread.sleep(1000)
                logInfo("正在退出当前进程，下一次启动 Jar：{}", selected.path)
                exitProcess(0)
            }, "bot-restart").apply { isDaemon = false }.start()
        } catch (e: Exception) {
            targetJar = null
            helper.destroyForcibly()
            throw e
        }
        status()
    }

    private fun startLauncher(java: File, command: List<String>): Process {
        val helperClass = "bot.wuliang.restart.RestartLauncher"
        val resource = helperClass.replace('.', '/') + ".class"
        val helperDirectory = File(workingDirectory, "$FILE_CACHE_PATH/restart-launcher")
        val helperFile = File(helperDirectory, resource)
        Files.createDirectories(helperFile.toPath().parent)
        val bytes =
            checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "重启辅助程序缺失，请重新构建后端" }
                .use { it.readBytes() }
        Files.write(helperFile.toPath(), bytes)
        val logFile = File(workingDirectory, "logs/restart.log")
        Files.createDirectories(logFile.toPath().parent)
        val parent = ProcessHandle.current()
        val process = ProcessBuilder(
            listOf(
                java.absolutePath, "-Xms8m", "-Xmx32m", "-XX:+UseSerialGC", "-cp", helperDirectory.path,
                helperClass, parent.pid().toString(), parent.info().startInstant().orElseThrow().toString(),
                workingDirectory.path, logFile.path
            ) + command
        ).directory(workingDirectory).redirectError(ProcessBuilder.Redirect.appendTo(logFile)).start()
        try {
            val ready = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine() }
            }.get(10, TimeUnit.SECONDS)
            check(ready == "READY" && process.isAlive) { "重启辅助程序未就绪，请查看 logs/restart.log" }
            return process
        } catch (e: Exception) {
            process.destroyForcibly()
            process.outputStream.close()
            throw IllegalStateException("无法启动重启辅助程序，请查看 logs/restart.log", e)
        }
    }

    fun deleteJar(name: String): Boolean = synchronized(lock) {
        check(targetJar == null) { "正在重启，暂时不能删除 Jar" }
        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\')) { "请选择运行目录中的 Jar 文件" }
        val file = resolveJar(name)
        val selected = configuration().path("jar_file").asText()
        val configuredFile =
            File(selected).let { if (it.isAbsolute) it else File(workingDirectory, selected) }.canonicalFile
        check(file != runningJar && file != configuredFile && !isJarRunning(file.path)) { "Jar 正在运行或已选为重启目标，不能删除" }
        Files.delete(file.toPath())
        true
    }

    fun isJarRunning(path: String): Boolean {
        val file = File(path).let { if (it.isAbsolute) it else File(workingDirectory, path) }.canonicalFile
        if (file == runningJar) return true
        return ProcessHandle.allProcesses().use { processes ->
            processes.anyMatch { process ->
                val args = process.info().arguments().orElse(null) ?: return@anyMatch false
                val jarIndex = args.indexOf("-jar")
                if (jarIndex < 0 || jarIndex + 1 >= args.size) false
                else {
                    val candidate = File(args[jarIndex + 1])
                    // 其他进程的相对路径缺少工作目录信息，按文件名保守保护。
                    if (candidate.isAbsolute) candidate.canonicalFile == file else candidate.name == file.name
                }
            }
        }
    }
}
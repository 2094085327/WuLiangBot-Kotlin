package bot.wuliang.imageProcess

import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import com.microsoft.playwright.Browser
import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.springframework.stereotype.Component
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** Playwright 的创建、截图和释放全部在同一线程执行。 */
@Component
class WebScreenshotRenderer {
    private val closed = AtomicBoolean(false)

    // 仅截图线程访问这两个对象，不把 Browser 交给调用方。
    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4),
        { worker ->
            Thread({
                try {
                    worker.run()
                } finally {
                    closeBrowser()
                }
            }, "web-screenshot").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun render(data: WebImgUtil.ImgData): ByteArray {
        check(!closed.get()) { "图片服务已关闭" }
        val task = FutureTask { capture(data) }
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            throw IllegalStateException("图片生成任务较多，请稍后重试。")
        }
        return try {
            task.get(90, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            task.cancel(true)
            executor.remove(task)
            Thread.currentThread().interrupt()
            throw error
        } catch (_: TimeoutException) {
            task.cancel(true)
            executor.remove(task)
            throw IllegalStateException("图片生成等待超时，请稍后重试。")
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun capture(data: WebImgUtil.ImgData): ByteArray {
        try {
            return currentBrowser().newContext().use { context ->
                context.setDefaultTimeout(30_000.0)
                context.setDefaultNavigationTimeout(30_000.0)
                val page = context.newPage()
                if (data.data != null) page.addInitScript("window.INJECTED_DATA = " + data.data)
                page.navigate(data.url)
                data.waitElement?.let { page.waitForSelector(it) }
                val element = data.element
                if (element == null) page.screenshot(Page.ScreenshotOptions().setFullPage(true))
                else {
                    val target = requireNotNull(page.querySelector(element)) { "图片目标元素不存在" }
                    target.screenshot(ElementHandle.ScreenshotOptions())
                }
            }
        } catch (error: Exception) {
            // 超时、进程退出或传输中断后，不复用可能已经失效的会话。
            closeBrowser()
            throw error
        } finally {
            if (Thread.currentThread().isInterrupted) closeBrowser()
        }
    }

    private fun currentBrowser(): Browser {
        browser?.takeIf { it.isConnected }?.let { return it }
        closeBrowser()
        val created = Playwright.create()
        playwright = created
        return try {
            created.chromium().launch().also { browser = it }
        } catch (error: Exception) {
            closeBrowser()
            throw error
        }
    }

    private fun closeBrowser() {
        val oldBrowser = browser
        val oldPlaywright = playwright
        browser = null
        playwright = null
        // 取消任务时先完成清理，再恢复中断标志。
        val interrupted = Thread.interrupted()
        try {
            try {
                oldBrowser?.close()
            } catch (error: Exception) {
                logWarn("截图浏览器关闭失败", error)
            } finally {
                try {
                    oldPlaywright?.close()
                } catch (error: Exception) {
                    logWarn("截图驱动关闭失败", error)
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    @PreDestroy
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        // 退出时取消等待任务；工作线程退出前在 finally 中关闭浏览器和驱动。
        executor.shutdownNow().forEach { (it as? Future<*>)?.cancel(false) }
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

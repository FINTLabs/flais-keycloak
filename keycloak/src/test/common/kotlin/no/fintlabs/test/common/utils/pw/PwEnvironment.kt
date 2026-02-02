package no.fintlabs.test.common.utils.pw

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import org.junit.jupiter.api.extension.ExtensionConfigurationException

/**
 * Manages a Playwright lifecycle for tests, providing browser sessions with tracing enabled.
 * Supports multiple browser types and exposes the current session for easy access
 * during test execution and cleanup.
 *
 * Note:
 * - Use debugger to run without headless (browsers will open)
 */
class PwEnvironment : AutoCloseable {
    private var playwright: Playwright? = null

    companion object {
        private val currentSessionTL = ThreadLocal<Session?>()

        fun currentSession(): Session? = currentSessionTL.get()

        private fun setCurrentSession(session: Session?) = currentSessionTL.set(session)
    }

    data class Session(
        val browserName: String,
        private val browser: Browser,
        private val context: BrowserContext,
        val page: Page,
    ) : AutoCloseable {
        override fun close() {
            runCatching { page.close() }
            runCatching { context.close() }
            runCatching { browser.close() }
            currentSessionTL.remove()
        }

        fun context(): BrowserContext = context
    }

    fun start() {
        if (playwright != null) return
        playwright = Playwright.create()
    }

    fun newSession(browserName: String): Session {
        val pw = playwright ?: throw ExtensionConfigurationException("PwEnvironment not started. Call start().")

        val browserType =
            when (browserName) {
                "chromium" -> pw.chromium()
                "firefox" -> pw.firefox()
                "webkit" -> pw.webkit()
                else -> throw ExtensionConfigurationException("Invalid browser: '$browserName'")
            }

        val isDebug =
            java.lang.management.ManagementFactory
                .getRuntimeMXBean()
                .inputArguments
                .any { it.contains("-agentlib:jdwp") }

        val browser =
            browserType.launch(
                BrowserType
                    .LaunchOptions()
                    .setHeadless(!isDebug),
            )

        val ctxOptions = Browser.NewContextOptions()
        val ctx = browser.newContext(ctxOptions)

        ctx.tracing().start(
            Tracing
                .StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true),
        )

        val page = ctx.newPage()

        val session =
            Session(
                browserName = browserName,
                browser = browser,
                context = ctx,
                page = page,
            )

        setCurrentSession(session)
        return session
    }

    override fun close() {
        currentSessionTL.get()?.close()
        currentSessionTL.remove()

        runCatching { playwright?.close() }
        playwright = null
    }
}

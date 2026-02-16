package no.fintlabs.test.common.extensions.pw

import com.microsoft.playwright.Page
import com.microsoft.playwright.Tracing
import no.fintlabs.test.common.utils.pw.PwEnvironment
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import java.nio.file.Files
import java.nio.file.Paths

class PwArtifactsOnFailureExtension :
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    TestExecutionExceptionHandler {
    val screenshotsPath = "artifacts/playwright/screenshots"
    val tracesPath = "artifacts/playwright/traces"

    private fun safeName(context: ExtensionContext): String {
        val className =
            context.testClass
                .map { it.simpleName }
                .orElse("UnknownClass")

        val testName =
            context.testMethod
                .map { it.name }
                .orElse("unknownTest")

        val browser =
            PwEnvironment.currentSession()?.browserName ?: "unknownBrowser"

        return "${className}_${testName}_$browser"
            .replace("\\W+".toRegex(), "_")
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        Files.createDirectories(Paths.get(screenshotsPath))
        Files.createDirectories(Paths.get(tracesPath))
    }

    override fun handleTestExecutionException(
        context: ExtensionContext,
        throwable: Throwable,
    ) {
        val session = PwEnvironment.currentSession()
        if (session != null) {
            val name = safeName(context)

            runCatching {
                session.page.screenshot(
                    Page
                        .ScreenshotOptions()
                        .setPath(Paths.get("$screenshotsPath/$name.png"))
                        .setFullPage(true),
                )
            }

            runCatching {
                session.context().tracing().stop(
                    Tracing
                        .StopOptions()
                        .setPath(Paths.get("$tracesPath/$name.zip")),
                )
            }
        }

        throw throwable
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val session = PwEnvironment.currentSession() ?: return
        runCatching { session.context().tracing().stop() }
    }
}

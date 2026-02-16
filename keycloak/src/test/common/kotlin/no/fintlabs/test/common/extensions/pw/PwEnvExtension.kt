package no.fintlabs.test.common.extensions.pw

import com.microsoft.playwright.Page
import no.fintlabs.test.common.extensions.SharedExtensionStore
import no.fintlabs.test.common.extensions.SharedExtensionStore.PW_BROWSER_NAME
import no.fintlabs.test.common.extensions.SharedExtensionStore.PW_ENV
import no.fintlabs.test.common.extensions.SharedExtensionStore.PW_PAGE
import no.fintlabs.test.common.extensions.SharedExtensionStore.PW_SESSION
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.pw.PwEnvironment
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream

class PwEnvExtension :
    BeforeAllCallback,
    AfterAllCallback,
    TestTemplateInvocationContextProvider,
    BeforeEachCallback,
    AfterEachCallback,
    ParameterResolver {
    private val allBrowsers = listOf("chromium", "firefox", "webkit")

    override fun supportsTestTemplate(context: ExtensionContext) = true

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<out TestTemplateInvocationContext> =
        allBrowsers
            .map { browser ->
                object : TestTemplateInvocationContext {
                    override fun getDisplayName(invocationIndex: Int) = browser

                    override fun getAdditionalExtensions(): List<Extension> =
                        listOf(
                            BeforeEachCallback { ec ->
                                methodStore(ec).put(PW_BROWSER_NAME, browser)
                            },
                        )
                }
            }.stream()

    override fun beforeAll(context: ExtensionContext) {
        sharedStore(context).get(SharedExtensionStore.KC_ENV, KcEnvironment::class.java)
            ?: throw ExtensionConfigurationException(
                "PwEnvExtension requires KcEnvExtension (KcEnvironment not found in shared store).",
            )

        val pwEnv = PwEnvironment().also { it.start() }
        classStore(context).put(PW_ENV, pwEnv)
    }

    override fun beforeEach(context: ExtensionContext) = Unit

    override fun afterAll(context: ExtensionContext) {
        classStore(context).remove(PW_ENV, PwEnvironment::class.java)?.close() ?: return
    }

    override fun afterEach(context: ExtensionContext) {
        methodStore(context).remove(PW_PAGE, Page::class.java)
        methodStore(context).remove(PW_SESSION, PwEnvironment.Session::class.java)?.close()
    }

    override fun supportsParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Boolean = pc.parameter.type == PwEnvironment.Session::class.java

    override fun resolveParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Any {
        if (pc.parameter.type != PwEnvironment.Session::class.java) {
            throw ParameterResolutionException("Unsupported parameter: ${pc.parameter.type}")
        }

        val store = methodStore(ec)

        val session: PwEnvironment.Session =
            store.get(PW_SESSION, PwEnvironment.Session::class.java)
                ?: run {
                    val pwEnv =
                        classStore(ec).get(PW_ENV, PwEnvironment::class.java)
                            ?: throw ExtensionConfigurationException("PwEnvironment not initialized")

                    val browser =
                        store.get(PW_BROWSER_NAME, String::class.java)
                            ?: throw ExtensionConfigurationException("Browser not set for this invocation")

                    pwEnv.newSession(browser).also {
                        store.put(PW_SESSION, it)
                    }
                }

        return session
    }

    private fun sharedStore(context: ExtensionContext) = context.root.getStore(SharedExtensionStore.NS)

    private fun classStore(context: ExtensionContext) =
        context.getStore(ExtensionContext.Namespace.create(javaClass, context.requiredTestClass))

    private fun methodStore(context: ExtensionContext) = context.getStore(ExtensionContext.Namespace.create(javaClass, context.uniqueId))
}

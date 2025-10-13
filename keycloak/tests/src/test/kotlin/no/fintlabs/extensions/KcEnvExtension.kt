package no.fintlabs.extensions

import no.fintlabs.config.KcConfig
import no.fintlabs.utils.KcComposeEnvironment
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JUnit 5 extension that manages the lifecycle of a [KcComposeEnvironment] for integration tests.
 *
 * Responsibilities:
 * - Starts a [KcComposeEnvironment] (Keycloak + Dex containers via Testcontainers/Compose) once per test class.
 * - Stops and cleans up the environment after all tests in the class have run.
 * - Provides parameter injection: test methods can declare a [KcComposeEnvironment] parameter,
 *   and JUnit will resolve it automatically using this extension.
 *
 * How it works:
 * - [beforeAll] creates and starts a new [KcComposeEnvironment], then stores it in the JUnit extension store.
 * - [afterAll] retrieves the environment from the store and stops it.
 * - [supportsParameter] and [resolveParameter] let you write tests with parameter injection.

 * Notes:
 * - The environment is created once per test class, not per test method.
 *
 * This extension is intended only for local/integration testing against a fixed environment.
 */
class KcEnvExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {

    override fun beforeAll(context: ExtensionContext) {

        val store = store(context)
        val env = KcComposeEnvironment().also { it.start() }
        store.put(ENV_KEY, env)

        val requested = "config/kc/external-realm.json"
        val resolved = resolveConfigPath(requested)
            ?: throw ExtensionConfigurationException(
                "Could not find config. Tried '$requested' from various roots."
            )

        val cfg = try {
            KcConfig.fromFile(resolved)
        } catch (t: Throwable) {
            throw ExtensionConfigurationException("Failed to load Keycloak config from '$resolved'", t)
        }
        store.put(CFG_KEY, cfg)
    }

    override fun afterAll(context: ExtensionContext) {
        val store = store(context)
        store.get(CFG_KEY, KcConfig::class.java)?.let { store.remove(CFG_KEY) }
        store.get(ENV_KEY, KcComposeEnvironment::class.java)?.let { it.close(); store.remove(ENV_KEY) }
    }

    override fun supportsParameter(pc: ParameterContext, ec: ExtensionContext): Boolean {
        val t = pc.parameter.type
        return t == KcComposeEnvironment::class.java || t == KcConfig::class.java
    }

    override fun resolveParameter(pc: ParameterContext, ec: ExtensionContext): Any {
        val t = pc.parameter.type
        val s = store(ec)
        return when (t) {
            KcComposeEnvironment::class.java ->
                s.get(ENV_KEY, KcComposeEnvironment::class.java)
                    ?: throw ExtensionConfigurationException("KcComposeEnvironment not initialized")

            KcConfig::class.java ->
                s.get(CFG_KEY, KcConfig::class.java)
                    ?: throw ExtensionConfigurationException("KcTestConfig not initialized")

            else -> throw ParameterResolutionException("Unsupported parameter: $t")
        }
    }

    private fun resolveConfigPath(requested: String): Path? {
        val p = Paths.get(requested)
        if (p.isAbsolute && Files.isRegularFile(p)) return p

        System.getProperty("project.rootDir")?.let { root ->
            val candidate = Paths.get(root).resolve(requested).normalize()
            if (Files.isRegularFile(candidate)) return candidate
        }

        return null
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store {
        val ns = ExtensionContext.Namespace.create(
            KcEnvExtension::class.java,
            context.requiredTestClass
        )
        return context.getStore(ns)
    }

    private companion object {
        const val ENV_KEY = "keycloak-env"
        const val CFG_KEY = "keycloak-config"
    }
}
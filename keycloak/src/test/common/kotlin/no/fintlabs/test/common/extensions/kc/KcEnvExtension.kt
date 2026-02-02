package no.fintlabs.test.common.extensions.kc

import no.fintlabs.test.common.config.KcConfig
import no.fintlabs.test.common.extensions.SharedExtensionStore.KC_CFG
import no.fintlabs.test.common.extensions.SharedExtensionStore.KC_ENV
import no.fintlabs.test.common.extensions.SharedExtensionStore.NS
import no.fintlabs.test.common.utils.kc.KcAdminClient
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.kc.LocalKcEnvironment
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
 * JUnit 5 extension that manages the lifecycle of a Keycloak environment for tests.
 *
 * Responsibilities:
 * - Starts a [KcEnvironment] (Keycloak + Support containers via Testcontainers/Compose) once.
 * - Stops and cleans up the environment after all tests in the class have run.
 * - Provides parameter injection: test methods can declare [KcEnvironment], [KcConfig] parameters,
 *   and JUnit will resolve it automatically using this extension.
 *
 * How it works:
 * - [beforeAll] creates and initializes components and puts them in store.
 * - [afterAll] retrieves the components from the store and cleans up.
 * - [supportsParameter] and [resolveParameter] let you write tests with parameter injection.
 *
 * This extension is intended for testing against a fixed environment.
 */
class KcEnvExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)

        val env: KcEnvironment =
            store.get(KC_ENV, KcEnvironment::class.java) ?: LocalKcEnvironment().also { it.start() }

        store.put(KC_ENV, env)

        val requested = "config/kc/external-realm.json"
        val resolved =
            resolveConfigPath(requested)
                ?: throw ExtensionConfigurationException(
                    "Could not find config. Tried '$requested' from various roots.",
                )

        val kcConfig =
            try {
                KcConfig.fromFile(resolved)
            } catch (t: Throwable) {
                throw ExtensionConfigurationException(
                    "Failed to load Keycloak config from '$resolved'",
                    t,
                )
            }
        store.put(KC_CFG, kcConfig)

        val kcJson =
            try {
                Files.readString(resolved)
            } catch (t: Throwable) {
                throw ExtensionConfigurationException(
                    "Failed to read Keycloak realm JSON from '$resolved'",
                    t,
                )
            }

        KcAdminClient.resetRealmFromJson(env, kcJson)
        KcAdminClient.patchIdpAuthorizationUrls(env, "external", env.authentikUrl())
    }

    override fun afterAll(context: ExtensionContext) = Unit

    override fun supportsParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Boolean {
        val t = pc.parameter.type
        return t == KcEnvironment::class.java || t == KcConfig::class.java
    }

    override fun resolveParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Any {
        val t = pc.parameter.type
        val s = store(ec)
        return when (t) {
            KcEnvironment::class.java -> {
                s.get(KC_ENV, KcEnvironment::class.java)
                    ?: throw ExtensionConfigurationException("KcEnvironment not initialized")
            }

            KcConfig::class.java -> {
                s.get(KC_CFG, KcConfig::class.java)
                    ?: throw ExtensionConfigurationException("KcConfig not initialized")
            }

            else -> {
                throw ParameterResolutionException("Unsupported parameter: $t")
            }
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

    private fun store(context: ExtensionContext): ExtensionContext.Store = context.root.getStore(NS)
}

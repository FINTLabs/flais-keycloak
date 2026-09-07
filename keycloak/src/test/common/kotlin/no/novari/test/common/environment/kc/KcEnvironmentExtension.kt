package no.novari.test.common.environment.kc

import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.SharedExtensionStore.KC_CFG
import no.novari.test.common.environment.SharedExtensionStore.KC_ENV
import no.novari.test.common.environment.SharedExtensionStore.NS
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.ConfigLoader
import no.novari.test.common.utils.KcAdminClient
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * JUnit 5 extension that manages the lifecycle of a Keycloak environment for tests.
 *
 * Responsibilities:
 * - Starts a [KcEnvironmentExtension] (Keycloak + Support containers via Testcontainers/Compose) once.
 * - Stops and cleans up the environment after all tests in the class have run.
 * - Provides parameter injection: test methods can declare [KcEnvironmentExtension], [KcConfig] parameters,
 *   and JUnit will resolve it automatically using this extension.
 *
 * How it works:
 * - [beforeAll] creates and initializes components and puts them in store.
 * - [afterAll] retrieves the components from the store and cleans up.
 * - [supportsParameter] and [resolveParameter] let you write tests with parameter injection.
 *
 * This extension is intended for testing against a fixed environment.
 */
class KcEnvironmentExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)

        val env: KcEnvironment =
            store.get(KC_ENV, KcEnvironment::class.java) ?: LocalKcEnvironment().also {
                it.start()
                store.put(KC_ENV, it)
            }
        val kcConfig = ConfigLoader.loadKeycloakRealm(Realms.EXTERNAL)
        store.put(KC_CFG, kcConfig)

        KcAdminClient.resetRealmFromJson(env, kcConfig.toJson())
        KcAdminClient.patchIdpAuthorizationUrls(env, Realms.EXTERNAL, env.authentikUrl())
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

    private fun store(context: ExtensionContext): ExtensionContext.Store = context.root.getStore(NS)
}

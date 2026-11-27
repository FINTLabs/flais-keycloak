package no.novari.test.common.environment.kc

import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.SharedExtensionStore.KC_CFG
import no.novari.test.common.environment.SharedExtensionStore.KC_ENV
import no.novari.test.common.environment.SharedExtensionStore.NS
import no.novari.test.common.environment.SharedExtensionStore.SCIM_USERS
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.ConfigLoader
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.reflect.ParameterizedType

class KcEnvironmentExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    override fun afterAll(context: ExtensionContext) = Unit

    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)

        val env =
            store.get(KC_ENV, KcEnvironment::class.java)
                ?: LocalKcEnvironment().also {
                    it.start()
                    store.put(KC_ENV, it)
                }

        val kcConfig = ConfigLoader.loadKeycloakRealm(Realms.EXTERNAL)
        store.put(KC_CFG, kcConfig)

        KcAdminClient.resetRealmFromJson(env, kcConfig.toJson())
        KcAdminClient.patchIdpBrowserUrls(
            env,
            Realms.EXTERNAL,
            env.mockIdpUrl(),
        )

        val users =
            listOf(Orgs.ROGALAND, Orgs.TELEMARK).flatMap { orgAlias ->
                ScimFlow.provision(
                    env = env,
                    kcConfig = kcConfig,
                    orgAlias = orgAlias,
                    users =
                        listOf(
                            ScimFlow.randomUser(orgAlias = orgAlias),
                        ),
                )
            }

        context.getStore(NS).put(SCIM_USERS, users)
    }

    override fun supportsParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Boolean =
        pc.parameter.type == KcEnvironment::class.java ||
            pc.parameter.type == KcConfig::class.java ||
            isScimUsersParameter(pc)

    override fun resolveParameter(
        pc: ParameterContext,
        ec: ExtensionContext,
    ): Any {
        if (isScimUsersParameter(pc)) {
            return ec.getStore(NS).get(SCIM_USERS)
                ?: throw ExtensionConfigurationException(
                    "SCIM users not initialized",
                )
        }

        val store = store(ec)

        return when (val type = pc.parameter.type) {
            KcEnvironment::class.java ->
                store.get(KC_ENV, KcEnvironment::class.java)
                    ?: throw ExtensionConfigurationException(
                        "KcEnvironment not initialized",
                    )

            KcConfig::class.java ->
                store.get(KC_CFG, KcConfig::class.java)
                    ?: throw ExtensionConfigurationException(
                        "KcConfig not initialized",
                    )

            else ->
                throw ParameterResolutionException(
                    "Unsupported parameter: $type",
                )
        }
    }

    private fun isScimUsersParameter(pc: ParameterContext): Boolean {
        val type =
            pc.parameter.parameterizedType as? ParameterizedType
                ?: return false

        return type.rawType == List::class.java &&
            type.actualTypeArguments.singleOrNull() == ScimUser::class.java
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store = context.root.getStore(NS)
}

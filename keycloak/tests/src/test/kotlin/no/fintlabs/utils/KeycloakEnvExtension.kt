package no.fintlabs.utils

import org.junit.jupiter.api.extension.*

class KeycloakEnvExtension :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {

    override fun beforeAll(context: ExtensionContext) {
        val store = store(context)
        val env = KeycloakComposeEnvironment().also { it.start() }
        store.put(ENV_KEY, env)
    }

    override fun afterAll(context: ExtensionContext) {
        val store = store(context)
        val env = store.get(ENV_KEY, KeycloakComposeEnvironment::class.java)
        env?.close()
        store.remove(ENV_KEY)
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        return parameterContext.parameter.type == KeycloakComposeEnvironment::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return store(extensionContext).get(ENV_KEY, KeycloakComposeEnvironment::class.java)
            ?: throw ExtensionConfigurationException("KeycloakComposeEnvironment not initialized")
    }

    private fun store(context: ExtensionContext): ExtensionContext.Store {
        val ns = ExtensionContext.Namespace.create(
            KeycloakEnvExtension::class.java,
            context.requiredTestClass
        )
        return context.getStore(ns)
    }

    private companion object {
        const val ENV_KEY = "keycloak-env"
    }
}

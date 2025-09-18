package no.fintlabs.authenticator

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgRedirectorAuthenticatorFactory : AuthenticatorFactory, ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-redirector-authenticator"
    private val orgRedirectorAuthenticator = OrgRedirectorAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgRedirectorAuthenticator

    override fun init(config: Config.Scope) {
        // No required actions needed
    }

    override fun postInit(sessionFactory: KeycloakSessionFactory) {
        // No required actions needed
    }

    override fun close() {
        // No required actions needed
    }

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "Org Redirector"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = false

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
            arrayOf(AuthenticationExecutionModel.Requirement.REQUIRED)

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String = "Redirects to identity provider from org selector"

    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf()
}

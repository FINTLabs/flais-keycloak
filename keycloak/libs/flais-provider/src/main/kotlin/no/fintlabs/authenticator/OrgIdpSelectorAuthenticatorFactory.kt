package no.fintlabs.authenticator

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgIdpSelectorAuthenticatorFactory : AuthenticatorFactory, ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-idp-selector-authenticator"
    private val orgIdpSelectorAuthenticator = OrgIdpSelectorAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgIdpSelectorAuthenticator

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

    override fun getDisplayType(): String = "Org Idp Selector"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = false

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
            arrayOf(AuthenticationExecutionModel.Requirement.REQUIRED)

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
            "Allows selecting an identity provider user should log in with"

    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf()
}

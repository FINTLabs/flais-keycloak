package no.fintlabs.authenticator

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgSelectorAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-selector-authenticator"
    private val orgSelectorAuthenticator: OrgSelectorAuthenticator = OrgSelectorAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgSelectorAuthenticator

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

    override fun getDisplayType(): String = "Org Selector"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
        )

    override fun isUserSetupAllowed(): Boolean = true

    override fun getHelpText(): String = "Allows selecting an organization that user should log in with"

    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf()
}

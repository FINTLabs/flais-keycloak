package no.novari.authenticator.org

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgSelectionUiAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-selection-ui-authenticator"
    private val orgSelectorAuthenticator: OrgSelectionUiAuthenticator =
        OrgSelectionUiAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgSelectorAuthenticator

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Organization Selection"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = true

    override fun getHelpText(): String =
        "Presents the user with a list of organizations they are permitted to log in with for the current client."

    override fun getConfigProperties(): List<ProviderConfigProperty> = mutableListOf()

    override fun init(config: Config.Scope) {
        // No required actions needed
    }

    override fun postInit(sessionFactory: KeycloakSessionFactory) {
        // No required actions needed
    }

    override fun close() {
        // No required actions needed
    }
}

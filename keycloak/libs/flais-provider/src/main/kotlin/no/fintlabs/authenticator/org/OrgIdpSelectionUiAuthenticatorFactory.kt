package no.fintlabs.authenticator.org

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgIdpSelectionUiAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-idp-selection-ui-authenticator"
    private val orgIdpSelectorAuthenticator = OrgIdpSelectionUiAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgIdpSelectorAuthenticator

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Organization Identity Provider Selection"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = false

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Presents the user with a selection of identity providers associated with the chosen organization. " +
            "If only one identity provider is available, the selection is skipped and the user is redirected automatically."

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

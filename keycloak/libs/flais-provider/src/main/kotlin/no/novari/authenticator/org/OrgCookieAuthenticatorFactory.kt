package no.novari.authenticator.org

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgCookieAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-cookie-authenticator"
    private val orgCookieAuthenticator = OrgCookieAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgCookieAuthenticator

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Organization SSO Cookie"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = false

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Validates the Keycloak SSO cookie and restores the FLAIS organization context from the existing user session. " +
            "Should be placed at the top of the browser flow to enable SSO for organization-aware clients."

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

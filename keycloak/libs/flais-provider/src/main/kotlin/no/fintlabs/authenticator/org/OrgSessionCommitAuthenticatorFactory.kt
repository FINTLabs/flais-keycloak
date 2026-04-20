package no.fintlabs.authenticator.org

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgSessionCommitAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-session-commit-authenticator"
    private val orgSessionCommitAuthenticator = OrgSessionCommitAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgSessionCommitAuthenticator

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Organization Session Commit"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = false

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Commits the selected organization into the authentication session, client notes, and user session. " +
            "Must be placed after the FLAIS Organization Selection step to ensure organization context is available downstream."

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

package no.novari.authenticator.client

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class ClientOrgAccessAuthenticatorFactory : AuthenticatorFactory {
    private val providerId = "client-org-access-authenticator"
    private val clientOrgAccessAuthenticator = ClientOrgAccessAuthenticator()

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Organization Client Access"

    override fun getReferenceCategory(): String = "client"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String = "Checks whether the organization for this login (idp) is allowed for the current client."

    override fun create(session: KeycloakSession): Authenticator = clientOrgAccessAuthenticator

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> = mutableListOf()

    override fun init(config: Config.Scope) {
        // No required actions needed
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        // No required actions needed
    }

    override fun close() {
        // No required actions needed
    }
}

package no.fintlabs.authenticator.client

import no.fintlabs.service.ClientOrgAccessService
import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class ClientOrgAccessAuthenticatorFactory : AuthenticatorFactory {
    private val providerId = "client-org-access-authenticator"
    private val clientOrgAccessAuthenticator = ClientOrgAccessAuthenticator(ClientOrgAccessService())

    companion object {
        const val CONFIG_MODE = "mode"
        const val MODE_POST_LOGIN = "post-login"
        const val MODE_BROWSER = "browser"
        const val CLIENT_NOTE_ORG_ALIAS = "org_alias"
    }

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

    override fun getHelpText(): String =
        "Checks whether the organization for this login is allowed for the current client. " +
            "Configure mode=post-login for broker post login flow, or mode=browser for browser/cookie flow."

    override fun create(session: KeycloakSession): Authenticator = clientOrgAccessAuthenticator

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> =
        mutableListOf(
            ProviderConfigProperty().apply {
                name = CONFIG_MODE
                label = "Mode"
                type = ProviderConfigProperty.LIST_TYPE
                options = listOf(MODE_POST_LOGIN, MODE_BROWSER)
                defaultValue = MODE_POST_LOGIN
                helpText = "post-login = use brokered identity context. browser = use browser/cookie logic."
            },
        )

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

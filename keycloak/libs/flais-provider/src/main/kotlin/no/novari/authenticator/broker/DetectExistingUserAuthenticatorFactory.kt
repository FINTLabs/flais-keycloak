package no.novari.authenticator.broker

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class DetectExistingUserAuthenticatorFactory : AuthenticatorFactory {
    private val providerId = "detect-existing-user-authenticator"
    private val detectExistingUserAuthenticator = DetectExistingUserAuthenticator()

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Detect Existing User"

    override fun getReferenceCategory(): String = "broker"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Detects an existing user for broker login. If no account matches, " +
            "displays an error page using the configured message key."

    override fun create(session: KeycloakSession): Authenticator = detectExistingUserAuthenticator

    override fun getConfigProperties(): MutableList<ProviderConfigProperty> =
        mutableListOf(
            ProviderConfigProperty().apply {
                name = "errorMessageKey"
                label = "Error message key"
                type = ProviderConfigProperty.STRING_TYPE
                helpText =
                    "Translation key displayed when no existing account matches."
                defaultValue = "accessDeniedMessage"
                isRequired = true
            },
        )

    override fun init(config: Config.Scope) {
        // No initialization needed
    }

    override fun postInit(factory: KeycloakSessionFactory) {
        // No initialization needed
    }

    override fun close() {
        // No resources to close
    }
}

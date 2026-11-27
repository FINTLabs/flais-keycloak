package no.novari.authenticator.org

import org.keycloak.Config
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorFactory
import org.keycloak.authentication.ConfigurableAuthenticatorFactory
import org.keycloak.models.AuthenticationExecutionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty

class OrgRememberUiAuthenticatorFactory :
    AuthenticatorFactory,
    ConfigurableAuthenticatorFactory {
    private val providerId: String = "org-remember-ui-authenticator"
    private val orgRememberAuthenticator = OrgRememberUiAuthenticator()

    override fun create(session: KeycloakSession): Authenticator = orgRememberAuthenticator

    override fun getId(): String = providerId

    override fun getDisplayType(): String = "FLAIS Remember Organization Confirmation"

    override fun getReferenceCategory(): String = "organization"

    override fun isConfigurable(): Boolean = true

    override fun getRequirementChoices(): Array<out AuthenticationExecutionModel.Requirement> =
        arrayOf(
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED,
        )

    override fun isUserSetupAllowed(): Boolean = false

    override fun getHelpText(): String =
        "Requires an explicit choice to remember or forget the selected organization before redirecting to the identity provider."

    override fun getConfigProperties(): List<ProviderConfigProperty> =
        listOf(
            ProviderConfigProperty().apply {
                name = OrgRememberUiAuthenticatorConfig.COOKIE_MAX_AGE_DAYS
                label = "Remember organization lifetime (days)"
                type = ProviderConfigProperty.STRING_TYPE
                defaultValue = OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS.toString()
                helpText = "How many days to remember the organization. Enter a positive whole number, up to 24855. " +
                    "Defaults to ${OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS} days when unset or invalid."
            },
        )

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

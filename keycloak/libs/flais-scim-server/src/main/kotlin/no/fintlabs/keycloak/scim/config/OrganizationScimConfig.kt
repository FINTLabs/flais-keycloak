package no.fintlabs.keycloak.scim.config

import org.keycloak.models.OrganizationModel

/**
 * SCIM configuration for organizations
 */
class OrganizationScimConfig(
    private val organization: OrganizationModel,
) : ScimConfig {
    @Throws(ConfigurationError::class)
    override fun validateConfig() {
        if (authenticationMode == ScimConfig.AuthenticationMode.EXTERNAL) {
            if (externalIssuer == null) {
                throw ConfigurationError("SCIM_EXTERNAL_ISSUER is not set")
            }

            if (externalJwksUri == null) {
                throw ConfigurationError("SCIM_EXTERNAL_JWKS_URI is not set")
            }

            if (externalAudience == null) {
                throw ConfigurationError("SCIM_EXTERNAL_AUDIENCE is not set")
            }
        }
    }

    override val authenticationMode =
        getAttribute("SCIM_AUTHENTICATION_MODE").let {
            if (it == null) ScimConfig.AuthenticationMode.KEYCLOAK else ScimConfig.AuthenticationMode.valueOf(it)
        }

    override val externalIssuer = getAttribute("SCIM_EXTERNAL_ISSUER")

    override val externalJwksUri = getAttribute("SCIM_EXTERNAL_JWKS_URI")

    override val externalAudience = getAttribute("SCIM_EXTERNAL_AUDIENCE")

    override val linkIdp = getAttribute("SCIM_LINK_IDP").equals("true", ignoreCase = true)

    override val emailAsUsername =
        getAttribute("SCIM_EMAIL_AS_USERNAME")
            .equals("true", ignoreCase = true)

    private fun getAttribute(attributeName: String): String? =
        organization.attributes
            ?.get(attributeName)
            ?.first()
}

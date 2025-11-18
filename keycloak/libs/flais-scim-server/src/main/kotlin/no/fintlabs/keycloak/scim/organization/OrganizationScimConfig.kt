package no.fintlabs.keycloak.scim.organization

import org.keycloak.models.OrganizationModel

/**
 * SCIM configuration for organizations
 */
class OrganizationScimConfig(
    private val organization: OrganizationModel
) : ScimConfig {

    @Throws(ConfigurationError::class)
    override fun validateConfig() {
        if (authenticationMode == null) {
            throw ConfigurationError("SCIM_AUTHENTICATION_MODE is not set")
        }

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
        } else {
            throw ConfigurationError(
                "SCIM_AUTHENTICATION_MODE $authenticationMode AuthenticationMode not supported in organization mode"
            )
        }
    }

    override fun getAuthenticationMode(): ScimConfig.AuthenticationMode? {
        val value = getAttribute("SCIM_AUTHENTICATION_MODE")
        if (value.isNullOrEmpty()) return null

        return ScimConfig.AuthenticationMode.valueOf(value)
    }

    override fun getExternalIssuer(): String? =
        getAttribute("SCIM_EXTERNAL_ISSUER")

    override fun getExternalJwksUri(): String? =
        getAttribute("SCIM_EXTERNAL_JWKS_URI")

    override fun getExternalAudience(): String? =
        getAttribute("SCIM_EXTERNAL_AUDIENCE")

    override fun getLinkIdp(): Boolean =
        getAttribute("SCIM_LINK_IDP").equals("true", ignoreCase = true)

    override fun getEmailAsUsername(): Boolean =
        getAttribute("SCIM_EMAIL_AS_USERNAME").equals("true", ignoreCase = true)

    /**
     * Gets an organization attribute value
     */
    private fun getAttribute(attributeName: String): String? {
        val attributes = organization.attributes ?: return null
        val values = attributes[attributeName] ?: return null
        if (values.isEmpty()) return null

        return values.first()
    }
}

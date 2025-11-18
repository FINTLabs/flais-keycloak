package no.fintlabs.keycloak.scim.config

import

/**
 * SCIM Configuration
 */
interface ScimConfig {

    /**
     * SCIM Authentication modes
     */
    enum class AuthenticationMode {
        KEYCLOAK,
        EXTERNAL
    }

    /**
     * Validates the configuration
     *
     * @throws ConfigurationError thrown if the configuration is invalid
     */
    @Throws(ConfigurationError::class)
    fun validateConfig()

    /**
     * Gets the SCIM Authentication mode
     *
     * @return authentication mode
     */
    fun getAuthenticationMode(): AuthenticationMode

    /**
     * Gets the external token issuer (if in EXTERNAL mode)
     *
     * @return external token issuer
     */
    fun getExternalIssuer(): String

    /**
     * Gets the external token JWKS URI (if in EXTERNAL mode)
     *
     * @return external token JWKS URI
     */
    fun getExternalJwksUri(): String

    /**
     * Gets the external audience (if in EXTERNAL mode)
     *
     * @return external audience
     */
    fun getExternalAudience(): String

    /**
     * Returns whether identity provider should be automatically linked
     *
     * @return true if identity provider should be automatically linked
     */
    fun getLinkIdp(): Boolean

    /**
     * Returns whether email should be used as username instead of username
     *
     * @return true if email should be used as username
     */
    fun getEmailAsUsername(): Boolean
}

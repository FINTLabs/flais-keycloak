package no.fintlabs.keycloak.scim.config

/**
 * SCIM Configuration
 */
interface ScimConfig {
    /**
     * SCIM Authentication modes
     */
    enum class AuthenticationMode {
        KEYCLOAK,
        EXTERNAL,
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
    val authenticationMode: AuthenticationMode

    /**
     * Gets the external token issuer (if in EXTERNAL mode)
     *
     * @return external token issuer
     */
    val externalIssuer: String?

    /**
     * Gets the external token JWKS URI (if in EXTERNAL mode)
     *
     * @return external token JWKS URI
     */
    val externalJwksUri: String?

    /**
     * Gets the external audience (if in EXTERNAL mode)
     *
     * @return external audience
     */
    val externalAudience: String?

    /**
     * Returns whether identity provider should be automatically linked
     *
     * @return true if identity provider should be automatically linked
     */
    val linkIdp: Boolean

    /**
     * Returns whether email should be used as username instead of username
     *
     * @return true if email should be used as username
     */
    val emailAsUsername: Boolean
}

package no.novari.keycloak.scim.config

/**
 * Configuration error
 */
class ConfigurationError(
    message: String,
) : Exception(message)

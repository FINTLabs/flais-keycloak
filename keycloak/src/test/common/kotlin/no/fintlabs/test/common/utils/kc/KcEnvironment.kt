package no.fintlabs.test.common.utils.kc

/**
 * Testcontainers-based environment for running Keycloak and related providers in integration tests.
 *
 * Simplifies the process of setting up the environment in tests.
 */
interface KcEnvironment : AutoCloseable {
    val keycloakAdminUser: String
    val keycloakAdminPassword: String

    fun keycloakInternalUrl(): String

    fun keycloakServiceUrl(): String

    fun keycloakManagementUrl(): String

    fun flaisScimAuthUrl(): String

    fun authentikUrl(): String

    fun flaisKeycloakDemoUrl(): String
}

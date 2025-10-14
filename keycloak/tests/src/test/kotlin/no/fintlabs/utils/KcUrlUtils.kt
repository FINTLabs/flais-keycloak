package no.fintlabs.utils

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Utility functions for constructing Keycloak-related URLs in integration tests.
 *
 * These helpers generate URLs for starting standard OpenID Connect flows
 * against the Keycloak instance running in [KcComposeEnvironment].
 *
 * Notes:
 * - The values are fixed for the dev/test environment (client_id, redirect_uri, etc.).
 * - Not intended for production use; these URLs are hardcoded to match the test setup.
 */
object KcUrlUtils {
    fun authStartUrl(
        env: KcComposeEnvironment,
        clientId: String,
    ): HttpUrl {
        val base = env.keycloakServiceUrl()
        val url = "$base/realms/external/protocol/openid-connect/auth"
        return url
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", "http://localhost:3000/callback")
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", "openid")
            .build()
    }
}

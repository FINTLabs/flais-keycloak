package no.fintlabs.utils

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.keycloak.util.JsonSerialization
import java.util.Base64

/**
 * Utility functions for handling Keycloak token exchange and validation.
 */
object KcToken {
    fun exchangeCodeForAccessToken(
        env: KcComposeEnvironment,
        realm: String,
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String,
        httpClient: OkHttpClient? = null,
    ): String {
        val client = httpClient ?: KcHttpClient.create()
        val tokenEndpoint = "${env.keycloakServiceUrl()}/realms/$realm/protocol/openid-connect/token"

        val form =
            FormBody
                .Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("client_id", clientId)
                .add("code_verifier", codeVerifier)
                .build()

        val request =
            Request
                .Builder()
                .url(tokenEndpoint)
                .post(form)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Token endpoint returned ${response.code}")
            }

            val body = response.body.string()
            val accessToken = extractField(body, "access_token")
            val tokenType = extractField(body, "token_type")

            if (accessToken.isNullOrBlank()) {
                throw IllegalStateException("Missing access_token in token response")
            }
            if (tokenType != null && tokenType != "Bearer") {
                throw IllegalStateException("Unexpected token_type: $tokenType")
            }

            return accessToken
        }
    }

    fun validateToken(
        env: KcComposeEnvironment,
        realm: String,
        accessToken: String,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = httpClient ?: KcHttpClient.create()
        val userinfoEndpoint = "${env.keycloakServiceUrl()}/realms/$realm/protocol/openid-connect/userinfo"

        val request =
            Request
                .Builder()
                .url(userinfoEndpoint)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("userinfo endpoint returned ${response.code}")
        }

        return response
    }

    fun decodeClaim(
        accessToken: String,
        claim: String,
    ): Any? {
        val parts = accessToken.split(".")
        require(parts.size == 3) { "Invalid JWT" }

        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payloadJson = String(payloadBytes, Charsets.UTF_8)

        val json =
            JsonSerialization.readValue(
                payloadJson.byteInputStream(),
                Map::class.java,
            ) as Map<*, *>

        return json[claim]
    }

    private fun extractField(
        body: String,
        field: String,
    ): String? = "\"$field\":\"([^\"]+)\"".toRegex().find(body)?.groupValues?.get(1)
}

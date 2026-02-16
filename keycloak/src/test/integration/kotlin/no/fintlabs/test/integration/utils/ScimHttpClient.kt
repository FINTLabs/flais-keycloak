package no.fintlabs.test.integration.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * OkHttp client for SCIM.
 *
 */
object ScimHttpClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenClient = OkHttpClient()

    @Serializable
    data class Token(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("expires_in")
        val expiresIn: Long,
        @SerialName("token_type")
        val tokenType: String,
        val oid: String,
        val iss: String,
        val aud: String,
    )

    fun create(tokenUrl: String): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val token = getAccessToken(tokenUrl)
                val requestWithBearer =
                    chain
                        .request()
                        .newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .build()

                chain.proceed(requestWithBearer)
            }.build()

    fun getAccessToken(tokenUrl: String): String {
        val request =
            Request
                .Builder()
                .url(tokenUrl)
                .build()

        tokenClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch bearer token. HTTP ${response.code}")
            }

            val body =
                response.body.string()

            val token = json.decodeFromString<Token>(body)

            return token.accessToken
        }
    }
}

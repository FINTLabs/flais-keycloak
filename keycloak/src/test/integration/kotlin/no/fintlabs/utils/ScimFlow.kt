package no.fintlabs.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Utility functions to simplify SCIM flows in integration tests.
 */
object ScimFlow {
    private val json =
        Json {
            prettyPrint = false
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

    @Serializable
    data class ScimUser(
        @Transient
        val id: String? = null,
        val externalId: String,
        val userName: String,
        val active: Boolean,
        val name: Name,
        val emails: List<Email>,
    ) {
        @Serializable
        data class Name(
            val givenName: String,
            val familyName: String,
        )

        @Serializable
        data class Email(
            val value: String,
            val primary: Boolean? = null,
            val type: String? = null,
        )
    }

    @Serializable
    data class PatchRequest(
        @SerialName("Operations")
        val operations: List<PatchOperation>,
    ) {
        @Serializable
        data class PatchOperation(
            val op: String,
            val path: String? = null,
            val value: JsonElement? = null,
        )
    }

    private fun resolveClient(
        httpClient: OkHttpClient?,
        tokenUrl: String?,
    ): OkHttpClient =
        httpClient ?: run {
            requireNotNull(tokenUrl) { "tokenUrl is required when httpClient is null" }
            ScimHttpClient.create(tokenUrl)
        }

    fun createUser(
        baseUrl: String,
        tokenUrl: String,
        user: ScimUser,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient, tokenUrl)
        val bodyString = json.encodeToString(user)
        val body = bodyString.toRequestBody()

        val request =
            Request
                .Builder()
                .url("$baseUrl/Users")
                .post(body)
                .build()

        return client.newCall(request).execute()
    }

    fun deleteUser(
        baseUrl: String,
        tokenUrl: String,
        id: String,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient, tokenUrl)
        val request =
            Request
                .Builder()
                .url("$baseUrl/Users/$id")
                .delete()
                .build()

        return client.newCall(request).execute()
    }

    fun updateUser(
        baseUrl: String,
        tokenUrl: String,
        id: String,
        user: ScimUser,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient, tokenUrl)
        val bodyString = json.encodeToString(ScimUser.serializer(), user)
        val body = bodyString.toRequestBody()

        val request =
            Request
                .Builder()
                .url("$baseUrl/Users/$id")
                .put(body)
                .build()

        return client.newCall(request).execute()
    }
}

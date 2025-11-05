package no.fintlabs.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
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
        val externalId: String,
        val userName: String,
        val active: Boolean,
        val name: Name,
        val emails: List<Email>,
        val groups: List<String> = emptyList(),
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

    private fun resolveClient(httpClient: OkHttpClient?) = httpClient ?: ScimHttpClient.create()

    private fun postJson(
        url: HttpUrl,
        jsonBody: String,
        client: OkHttpClient,
    ): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        val req =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()

        return client.newCall(req).execute()
    }

    fun provisionUsers(
        baseUrl: String,
        orgId: String,
        users: List<ScimUser>,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val payload = json.encodeToString(users)
        return postJson("$baseUrl/provision/$orgId".toHttpUrl(), payload, client)
    }

    fun deprovisionUsers(
        baseUrl: String,
        orgId: String,
        users: List<ScimUser>,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val payload = json.encodeToString(users)
        return postJson("$baseUrl/deprovision/$orgId".toHttpUrl(), payload, client)
    }
}

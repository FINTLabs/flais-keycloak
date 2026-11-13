package no.novari.test.common.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.UUID
import kotlin.random.Random

/**
 * Utility functions to simplify SCIM flows in integration and system tests.
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
        var schemas: List<String>,
        @Transient
        var id: String? = null,
        var externalId: String,
        var userName: String,
        var active: Boolean,
        var emails: List<Email>? = null,
        var roles: List<Role>? = null,
        @SerialName("urn:ietf:params:scim:schemas:extension:fint:2.0:User")
        var fintUserExtension: FintUserExtension? = null,
    ) {
        @Serializable
        data class Email(
            var value: String,
            var primary: Boolean? = null,
            var type: String? = null,
        )

        @Serializable
        data class Role(
            var value: String? = null,
            var display: String? = null,
            var type: String? = null,
            var primary: Boolean? = null,
        )

        @Serializable
        data class FintUserExtension(
            var givenName: String? = null,
            var familyName: String? = null,
            var employeeId: String? = null,
            var studentNumber: String? = null,
            var userPrincipalName: String? = null,
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

    fun listUsers(
        baseUrl: String,
        tokenUrl: String,
        filter: String? = null,
        startIndex: Int? = null,
        count: Int? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        cursor: String? = null,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient, tokenUrl)
        val url =
            "$baseUrl/Users"
                .toHttpUrl()
                .newBuilder()
                .apply {
                    filter?.let { addQueryParameter("filter", it) }
                    startIndex?.let { addQueryParameter("startIndex", it.toString()) }
                    count?.let { addQueryParameter("count", it.toString()) }
                    sortBy?.let { addQueryParameter("sortBy", it) }
                    sortOrder?.let { addQueryParameter("sortOrder", it) }
                    cursor?.let { addQueryParameter("cursor", it) }
                }.build()

        val request =
            Request
                .Builder()
                .url(url)
                .get()
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

    fun patchUser(
        baseUrl: String,
        tokenUrl: String,
        id: String,
        operation: PatchRequest,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient, tokenUrl)
        val bodyString = json.encodeToString(PatchRequest.serializer(), operation)
        val body = bodyString.toRequestBody()

        val request =
            Request
                .Builder()
                .url("$baseUrl/Users/$id")
                .patch(body)
                .build()

        return client.newCall(request).execute()
    }

    fun randomUser(
        orgAlias: String,
        externalId: String = UUID.randomUUID().toString(),
        userName: String = externalId,
        givenName: String = listOf("Alice", "Jon", "Emma", "Lars").random(),
        familyName: String = "Basic",
        domain: String =
            when (orgAlias) {
                Orgs.TELEMARK -> "telemark.no"
                Orgs.ROGALAND -> "rogaland.no"
                else -> error("Unsupported organization: $orgAlias")
            },
        email: String? =
            "${givenName.lowercase()}.${familyName.lowercase()}.$externalId@$domain",
        active: Boolean = true,
        employeeId: String? = Random.nextInt(1000, 10000).toString(),
        studentNumber: String? = employeeId,
        userPrincipalName: String? = email,
        roles: List<ScimUser.Role>? =
            List(Random.nextInt(1, 4)) {
                val role = "role-${UUID.randomUUID()}"
                ScimUser.Role(
                    value = role,
                    display = role,
                    type = "WindowsAzureActiveDirectoryRole",
                    primary = false,
                )
            },
        fintUserExtension: ScimUser.FintUserExtension? =
            ScimUser.FintUserExtension(
                givenName = givenName,
                familyName = familyName,
                employeeId = employeeId,
                studentNumber = studentNumber,
                userPrincipalName = userPrincipalName,
            ),
    ): ScimUser =
        ScimUser(
            schemas =
                buildList {
                    add("urn:ietf:params:scim:schemas:core:2.0:User")
                    if (fintUserExtension != null) {
                        add("urn:ietf:params:scim:schemas:extension:fint:2.0:User")
                    }
                },
            externalId = externalId,
            userName = userName,
            active = active,
            emails = email?.let { listOf(ScimUser.Email(it, primary = true)) },
            roles = roles,
            fintUserExtension = fintUserExtension,
        )

    fun provision(
        env: KcEnvironment,
        kcConfig: KcConfig,
        orgAlias: String,
        users: List<ScimUser>,
    ): List<ScimUser> {
        val baseUrl =
            "${env.keycloakServiceUrl()}/realms/${Realms.EXTERNAL}/scim/v2/${kcConfig.requireOrg(orgAlias).id}"
        val tokenUrl = "${env.flaisScimAuthUrl()}/token"
        val client = resolveClient(null, tokenUrl)

        users.forEach { user ->
            createUser(baseUrl, tokenUrl, user, client).use { response ->
                val body = requireNotNull(response.body).string()

                check(response.code == 201) {
                    "Failed to provision ${user.userName} in $orgAlias: " +
                        "${response.code}: $body"
                }

                user.id =
                    Json
                        .parseToJsonElement(body)
                        .jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
            }
        }

        return users
    }
}

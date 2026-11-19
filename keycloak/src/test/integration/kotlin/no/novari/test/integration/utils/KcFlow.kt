package no.novari.test.integration.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.utils.KcUrl
import no.novari.test.common.utils.ScimFlow
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Utility functions to simplify Keycloak login flows in integration tests.
 */
object KcFlow {
    private fun resolveClient(httpClient: OkHttpClient?) = httpClient ?: KcHttpClient.create()

    private fun resolveAuthUrl(
        httpUrl: HttpUrl?,
        env: KcEnvironment?,
        clientId: String?,
        scope: String? = null,
    ): HttpUrl =
        httpUrl ?: run {
            requireNotNull(env) { "env is required when httpUrl is null" }
            requireNotNull(clientId) { "clientId is required when httpUrl is null" }
            KcUrl.authUrl(env, clientId, scope = scope).first
        }

    private fun get(
        url: HttpUrl,
        client: OkHttpClient,
    ): Response = client.newCall(Request.Builder().url(url).build()).execute()

    private fun post(
        url: HttpUrl,
        params: Map<String, String>,
        client: OkHttpClient,
    ): Response {
        val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
        val req =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()
        return client.newCall(req).execute()
    }

    private fun loginWithUser(
        env: KcEnvironment,
        clientId: String,
        orgAlias: String,
        idpAlias: String,
        username: String,
        claims: JsonObject = buildJsonObject {},
        httpClient: OkHttpClient? = null,
        httpUrl: HttpUrl? = null,
        hasIdpSelector: Boolean = true,
        scope: String? = null,
    ): Response {
        val client = httpClient ?: KcHttpClient.create(followRedirects = true)
        val url = resolveAuthUrl(httpUrl, env, clientId, scope)

        selectOrgAndContinueToIdpSelector(env, clientId, orgAlias, client, url).use { resp ->
            if (hasIdpSelector) {
                val kc = KcContextParser.parseKcContext(resp.body.string())
                continueFromIdpSelector(kc.url.loginAction!!, idpAlias, client).use { resp ->
                    return continueFromMockIdp(resp.request.url, username, client, claims)
                }
            } else {
                return continueFromMockIdp(resp.request.url, username, client, claims)
            }
        }
    }

    fun openAuthUrl(
        httpUrl: HttpUrl? = null,
        env: KcEnvironment? = null,
        clientId: String? = null,
        httpClient: OkHttpClient? = null,
        scope: String? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val url = resolveAuthUrl(httpUrl, env, clientId, scope)
        return get(url, client)
    }

    fun continueFromOrgSelector(
        url: HttpUrl,
        orgAlias: String,
        httpClient: OkHttpClient? = null,
    ): Response = post(url, mapOf("selected_org" to orgAlias), resolveClient(httpClient))

    fun continueFromIdpSelector(
        url: HttpUrl,
        idpAlias: String,
        httpClient: OkHttpClient? = null,
    ): Response = post(url, mapOf("identity_provider" to idpAlias), resolveClient(httpClient))

    fun continueFromMockIdp(
        url: HttpUrl,
        username: String,
        httpClient: OkHttpClient? = null,
        claims: JsonObject = buildJsonObject {},
    ): Response {
        require(url.encodedPath.endsWith("/authorize")) {
            "Expected mock IDP authorize URL: $url"
        }

        return post(
            url,
            mapOf(
                "username" to username,
                "claims" to claims.toString(),
            ),
            resolveClient(httpClient),
        )
    }

    fun selectOrgAndContinueToIdpSelector(
        env: KcEnvironment,
        clientId: String,
        orgAlias: String,
        httpClient: OkHttpClient? = null,
        httpUrl: HttpUrl? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val url = resolveAuthUrl(httpUrl, env, clientId)

        openAuthUrl(url, httpClient = client).use { resp ->
            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)
            return continueFromOrgSelector(kc.url.loginAction!!, orgAlias, client)
        }
    }

    fun loginWithMicrosoftScimUser(
        env: KcEnvironment,
        clientId: String,
        orgAlias: String,
        idpAlias: String,
        user: ScimFlow.ScimUser,
        httpClient: OkHttpClient? = null,
        httpUrl: HttpUrl? = null,
        hasIdpSelector: Boolean = true,
        scope: String? = null,
    ): Response =
        loginWithUser(
            env = env,
            clientId = clientId,
            orgAlias = orgAlias,
            idpAlias = idpAlias,
            username = user.userName,
            httpClient = httpClient,
            httpUrl = httpUrl,
            hasIdpSelector = hasIdpSelector,
            scope = scope,
            claims =
                buildJsonObject {
                    put("oid", user.externalId)
                    put(
                        "roles",
                        JsonArray(
                            user.roles.orEmpty().mapNotNull { role ->
                                role.value?.let { JsonPrimitive(it) }
                            },
                        ),
                    )
                },
        )

    fun loginWithIdportenUser(
        env: KcEnvironment,
        clientId: String,
        orgAlias: String,
        idpAlias: String,
        pid: String,
        httpClient: OkHttpClient? = null,
        hasIdpSelector: Boolean = false,
    ): Response =
        loginWithUser(
            env = env,
            clientId = clientId,
            orgAlias = orgAlias,
            idpAlias = idpAlias,
            username = pid,
            httpClient = httpClient,
            hasIdpSelector = hasIdpSelector,
            claims =
                buildJsonObject {
                    put("pid", pid)
                },
        )
}

package no.fintlabs.utils

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Utility functions for simplified Keycloak login flows in integration tests.
 */
object KcFlow {
    private fun resolveClient(httpClient: OkHttpClient?) = httpClient ?: KcHttpClient.create()

    private fun resolveAuthUrl(
        httpUrl: HttpUrl?,
        env: KcComposeEnvironment?,
        clientId: String?,
    ): HttpUrl =
        httpUrl ?: run {
            requireNotNull(env) { "env is required when httpUrl is null" }
            requireNotNull(clientId) { "clientId is required when httpUrl is null" }
            KcUrl.authUrl(env, clientId).first
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

    fun openAuthUrl(
        httpUrl: HttpUrl? = null,
        env: KcComposeEnvironment? = null,
        clientId: String? = null,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val url = resolveAuthUrl(httpUrl, env, clientId)
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

    fun continueFromDexIdp(
        url: HttpUrl,
        username: String,
        password: String,
        httpClient: OkHttpClient? = null,
    ): Response =
        post(
            url,
            mapOf("login" to username, "password" to password),
            resolveClient(httpClient),
        )

    fun selectOrgAndContinueToIdpSelector(
        env: KcComposeEnvironment,
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

    fun loginWithUser(
        env: KcComposeEnvironment,
        clientId: String,
        orgAlias: String,
        idpAlias: String,
        username: String,
        password: String,
        httpClient: OkHttpClient? = null,
        httpUrl: HttpUrl? = null,
    ): Response {
        val client = httpClient ?: KcHttpClient.create(followRedirects = true)
        val url = resolveAuthUrl(httpUrl, env, clientId)

        selectOrgAndContinueToIdpSelector(env, clientId, orgAlias, client, url).use { resp1 ->
            val kc1 = KcContextParser.parseKcContext(resp1.body.string())
            continueFromIdpSelector(kc1.url.loginAction!!, idpAlias, client).use { resp2 ->
                return continueFromDexIdp(resp2.request.url, username, password, client)
            }
        }
    }
}

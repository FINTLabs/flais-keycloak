package no.fintlabs.utils

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Utility functions to simplify Keycloak login flows in integration tests.
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

    fun continueFromAuthentikIdp(
        url: HttpUrl,
        username: String,
        password: String,
        httpClient: OkHttpClient? = null,
    ): Response {
        val client = resolveClient(httpClient)
        val jsonMediaType = "application/json".toMediaType()

        val flowUrl =
            if (url.encodedPath.contains("/application/o/authorize/")) {
                client.newCall(Request.Builder().url(url).build()).execute().use { it.request.url }
            } else {
                url
            }

        val flowSlug =
            flowUrl.pathSegments.let { segments ->
                val idx = segments.indexOf("flow")
                if (idx >= 0 &&
                    idx + 1 < segments.size
                ) {
                    segments[idx + 1]
                } else {
                    "default-authentication-flow"
                }
            }
        val apiUrl = flowUrl.newBuilder().encodedPath("/api/v3/flows/executor/$flowSlug/").build()

        fun apiRequest(body: String? = null): Response {
            val builder = Request.Builder().url(apiUrl).header("Accept", "application/json")
            if (body != null) builder.post(body.toRequestBody(jsonMediaType)) else builder.get()
            return client.newCall(builder.build()).execute()
        }

        apiRequest().use { if (it.code != 200) return it }

        apiRequest("""{"uid_field":"$username"}""").use { if (it.code != 200) return it }

        val authResp = apiRequest("""{"password":"$password"}""")
        val authBody = authResp.body.string()

        val redirectMatch = Regex("\"to\":\\s*\"([^\"]+)\"").find(authBody)
        if (redirectMatch != null) {
            var redirectPath = redirectMatch.groupValues[1].replace("\\/", "/")

            if (redirectPath == "/") {
                redirectPath = flowUrl.queryParameter("next") ?: redirectPath
            }

            val redirectUrl =
                when {
                    redirectPath.startsWith("http") -> redirectPath
                    else -> {
                        val (path, query) =
                            redirectPath.split('?', limit = 2).let {
                                it[0] to it.getOrNull(1)
                            }
                        flowUrl
                            .newBuilder()
                            .encodedPath(path)
                            .apply { query?.let { encodedQuery(it) } ?: query(null) }
                            .build()
                            .toString()
                    }
                }

            return client.newCall(Request.Builder().url(redirectUrl).build()).execute()
        }

        return authResp
    }

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
                return continueFromAuthentikIdp(resp2.request.url, username, password, client)
            }
        }
    }
}

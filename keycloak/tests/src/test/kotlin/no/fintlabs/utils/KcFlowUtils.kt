package no.fintlabs.utils

import okhttp3.*

/**
 * Utility functions for driving simplified Keycloak login flows in integration tests.
 *
 * Right now it contains a helper for selecting an organization and continuing to the IDP redirect,
 * mimicking what a browser would do in the flais-org-selector page.
 */
object KcFlowUtils {

    fun openOrgSelector(env: KcComposeEnvironment, clientId: String, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val startReq = Request.Builder()
            .url(KcUrlUtils.authStartUrl(env, clientId))
            .build()
        return client.newCall(startReq).execute()
    }

    fun continueFromOrgSelector(url: HttpUrl, orgAlias: String, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val body = FormBody.Builder()
            .add("selected_org", orgAlias)
            .build()

        val postReq = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return client.newCall(postReq).execute()
    }

    fun continueFromIdpSelector(url: HttpUrl, idpAlias: String, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val body = FormBody.Builder()
            .add("identity_provider", idpAlias)
            .build()

        val postReq = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return client.newCall(postReq).execute()
    }

    fun continueFromDexIdp(
        url: HttpUrl,
        username: String,
        password: String,
        httpClient: OkHttpClient? = null
    ): Response {
        val client = httpClient ?: KcHttpClient.create()
        val body = FormBody.Builder()
            .add("login", username)
            .add("password", password)
            .build()

        val postReq = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return client.newCall(postReq).execute()
    }

    fun selectOrgAndContinueToIdpSelector(
        env: KcComposeEnvironment,
        clientId: String,
        orgAlias: String,
        httpClient: OkHttpClient? = null
    ): Response {
        val client = httpClient ?: KcHttpClient.create()
        openOrgSelector(env, clientId, client).use { resp ->
            val html = resp.body.string()
            val kcContext = KcContextParser.parseKcContext(html)
            return continueFromOrgSelector(kcContext.url.loginAction!!, orgAlias, client)
        }
    }

    fun loginWithUser(
        env: KcComposeEnvironment,
        clientId: String,
        orgAlias: String,
        idpAlias: String,
        username: String,
        password: String,
        httpClient: OkHttpClient? = null
    ): Response {
        val client = httpClient ?: KcHttpClient.create(followRedirects = true)
        selectOrgAndContinueToIdpSelector(env, clientId, orgAlias, client).use { resp ->
            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, idpAlias, client).use { resp ->
                val finalUrl = resp.request.url

                return continueFromDexIdp(finalUrl, username, password, client)
            }
        }
    }
}

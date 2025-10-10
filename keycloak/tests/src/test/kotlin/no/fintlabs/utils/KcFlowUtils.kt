package no.fintlabs.utils

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Utility functions for driving simplified Keycloak login flows in integration tests.
 *
 * Right now it contains a helper for selecting an organization and continuing to the IDP redirect,
 * mimicking what a browser would do in the flais-org-selector page.
 */
object KcFlowUtils {

    fun openOrgSelector(env: KcComposeEnvironment, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val startReq = Request.Builder()
            .url(KcUrlUtils.authStartUrl(env))
            .build()
        return client.newCall(startReq).execute()
    }

    fun continueFromOrgSelector(actionUrl: HttpUrl, orgAlias: String, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val body = FormBody.Builder()
            .add("selected_org", orgAlias)
            .build()

        val postReq = Request.Builder()
            .url(actionUrl)
            .post(body)
            .build()

        return client.newCall(postReq).execute()
    }

    fun continueFromIdpSelector(actionUrl: HttpUrl, idpAlias: String, httpClient: OkHttpClient? = null): Response {
        val client = httpClient ?: KcHttpClient.create()
        val body = FormBody.Builder()
            .add("identity_provider", idpAlias)
            .build()

        val postReq = Request.Builder()
            .url(actionUrl)
            .post(body)
            .build()

        return client.newCall(postReq).execute()
    }

    fun selectOrgAndContinueToIdpSelector(
        env: KcComposeEnvironment,
        orgAlias: String,
        httpClient: OkHttpClient? = null
    ): Response {
        val client = httpClient ?: KcHttpClient.create()
        openOrgSelector(env, client).use { resp ->
            val html = resp.body.string()
            val kcContext = KcContextParser.parseKcContext(html)
            return continueFromOrgSelector(kcContext.url.loginAction!!, orgAlias, client)
        }
    }
}

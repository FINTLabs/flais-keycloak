package no.fintlabs.utils

import okhttp3.*

/**
 * OkHttp client for local Keycloak tests.
 *
 * - Disables redirects (so you can assert Location headers).
 * - Keeps cookies in memory for the life of the client.
 * - Always strips `Secure` so cookies set by Keycloak work on http://localhost.
 */
object KcHttpClient {
    fun create(): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(object : CookieJar {
                private val store = mutableListOf<Cookie>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookies.forEach { c ->
                        val adjusted = Cookie.Builder()
                            .name(c.name)
                            .value(c.value)
                            .hostOnlyDomain(url.host)
                            .path(c.path)
                            .apply { if (c.persistent) expiresAt(c.expiresAt) }
                            .build()

                        store.removeAll { it.name == adjusted.name && it.domain == adjusted.domain && it.path == adjusted.path }
                        store += adjusted
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    store.filter { it.matches(url) }
            })
            .build()
}
package no.fintlabs.test.integration.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * OkHttp client for local Keycloak tests.
 *
 * - Disables redirects by default (to assert Location headers).
 * - Keeps cookies in memory for the life of the client.
 * - Always strips `Secure` so cookies set by Keycloak work on http://localhost.
 */
object KcHttpClient {
    fun create(
        followRedirects: Boolean = false,
        followSslRedirects: Boolean = false,
    ): OkHttpClient {
        val cookieJar =
            object : CookieJar {
                private val store = mutableListOf<Cookie>()

                override fun saveFromResponse(
                    url: HttpUrl,
                    cookies: List<Cookie>,
                ) {
                    cookies.forEach { c ->
                        val adjusted =
                            Cookie
                                .Builder()
                                .name(c.name)
                                .value(c.value)
                                .hostOnlyDomain(url.host)
                                .path(c.path)
                                .apply { if (c.persistent) expiresAt(c.expiresAt) }
                                .build()

                        store.removeAll {
                            it.name == adjusted.name &&
                                it.domain == adjusted.domain &&
                                it.path == adjusted.path
                        }
                        store += adjusted
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }
            }

        return OkHttpClient
            .Builder()
            .followRedirects(followRedirects)
            .followSslRedirects(followSslRedirects)
            .addInterceptor { chain ->
                val request = chain.request()

                if (request.url.encodedPath.startsWith("/api/v3/flows/executor/") &&
                    request.method == "POST"
                ) {
                    val csrfCookie =
                        cookieJar
                            .loadForRequest(request.url)
                            .find {
                                it.name in
                                    listOf(
                                        "authentik_csrf",
                                        "csrftoken",
                                        "ak_csrf_token",
                                    )
                            }

                    if (csrfCookie != null) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .header("X-Authentik-Csrf", csrfCookie.value)
                                .build(),
                        )
                    }
                }

                chain.proceed(request)
            }.cookieJar(cookieJar)
            .build()
    }
}

package no.fintlabs.utils

import okhttp3.OkHttpClient

/**
 * OkHttp client for SCIM.
 *
 */
object ScimHttpClient {
    fun create(): OkHttpClient =
        OkHttpClient
            .Builder()
            .build()
}

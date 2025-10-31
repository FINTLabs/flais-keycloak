package no.fintlabs.utils

import okhttp3.OkHttpClient

object ScimHttpClient {
    fun create(): OkHttpClient =
        OkHttpClient
            .Builder()
            .build()
}

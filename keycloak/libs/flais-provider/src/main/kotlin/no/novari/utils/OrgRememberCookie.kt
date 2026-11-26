package no.novari.utils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object OrgRememberCookie {
    fun encodeValue(alias: String): String = URLEncoder.encode(alias, StandardCharsets.UTF_8)

    fun decodeValue(value: String): String? =
        try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }

    fun cookieName(clientId: String): String {
        val encodedClientId =
            URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        return "FLAIS_REMEMBERED_ORG_$encodedClientId"
    }
}

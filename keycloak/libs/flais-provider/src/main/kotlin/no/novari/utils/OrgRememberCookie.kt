package no.novari.utils

import java.util.Base64

object OrgRememberCookie {
    fun cookieName(clientId: String): String {
        val encodedClientId =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(clientId.toByteArray(Charsets.UTF_8))
        return "FLAIS_REMEMBERED_ORG_$encodedClientId"
    }
}

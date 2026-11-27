package no.novari.utils

import jakarta.ws.rs.core.NewCookie
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.services.Urls
import org.keycloak.utils.SecureContextResolver
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object OrgRememberCookie {
    fun read(context: AuthenticationFlowContext): String? =
        context.httpRequest.httpHeaders.cookies[cookieName(context.authenticationSession.client.clientId)]
            ?.value
            ?.let(::decodeValue)

    fun build(
        context: AuthenticationFlowContext,
        organizationAlias: String?,
        maxAge: Int,
    ): NewCookie {
        val path =
            Urls
                .realmBase(context.uriInfo.baseUri)
                .path("{realm}")
                .build(context.realm.name)
                .path
        return NewCookie
            .Builder(cookieName(context.authenticationSession.client.clientId))
            .value(organizationAlias?.let(::encodeValue) ?: "")
            .path(path)
            .maxAge(if (organizationAlias != null) maxAge else 0)
            .secure(SecureContextResolver.isSecureContext(context.session))
            .httpOnly(true)
            .sameSite(NewCookie.SameSite.LAX)
            .build()
    }

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

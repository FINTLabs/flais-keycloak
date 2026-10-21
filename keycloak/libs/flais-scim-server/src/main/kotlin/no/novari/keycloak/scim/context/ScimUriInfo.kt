package no.novari.keycloak.scim.context

import jakarta.ws.rs.core.UriBuilder
import jakarta.ws.rs.core.UriInfo
import java.net.URI

class ScimUriInfo(
    private val delegate: UriInfo,
    private val scimBaseUri: URI,
) : UriInfo by delegate {
    override fun getBaseUri(): URI = scimBaseUri

    override fun getBaseUriBuilder(): UriBuilder = UriBuilder.fromUri(scimBaseUri)
}

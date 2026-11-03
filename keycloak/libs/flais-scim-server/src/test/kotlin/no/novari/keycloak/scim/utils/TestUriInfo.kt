package no.novari.keycloak.scim.utils

import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.PathSegment
import jakarta.ws.rs.core.UriBuilder
import jakarta.ws.rs.core.UriInfo
import org.keycloak.models.KeycloakUriInfo
import java.net.URI

class TestUriInfo(
    private val requestUri: URI,
    private val queryParams: MultivaluedMap<String, String> = MultivaluedHashMap(),
    private val pathParams: MultivaluedMap<String, String> = MultivaluedHashMap(),
) : UriInfo {
    override fun getRequestUri(): URI = requestUri

    override fun getAbsolutePath(): URI =
        UriBuilder
            .fromUri(requestUri)
            .replaceQuery(null)
            .fragment(null)
            .build()

    override fun getBaseUri(): URI = URI("http://localhost")

    override fun getPath(): String = requestUri.path

    override fun getPath(decode: Boolean): String = path

    override fun getPathSegments(): List<PathSegment?> {
        TODO("Not yet implemented")
    }

    override fun getPathSegments(decode: Boolean): List<PathSegment?> {
        TODO("Not yet implemented")
    }

    override fun getQueryParameters(): MultivaluedMap<String, String> = queryParams

    override fun getQueryParameters(decode: Boolean): MultivaluedMap<String, String> = queryParams

    override fun getPathParameters(): MultivaluedMap<String, String> = pathParams

    override fun getPathParameters(decode: Boolean): MultivaluedMap<String, String> = pathParams

    override fun getMatchedURIs(): MutableList<String> = mutableListOf()

    override fun getMatchedURIs(decode: Boolean): MutableList<String> = mutableListOf()

    override fun getMatchedResources(): MutableList<Any> = mutableListOf()

    override fun resolve(uri: URI?): URI? = uri

    override fun relativize(uri: URI?): URI? = uri

    override fun getBaseUriBuilder(): UriBuilder = UriBuilder.fromUri(baseUri)

    override fun getRequestUriBuilder(): UriBuilder = UriBuilder.fromUri(requestUri)

    override fun getAbsolutePathBuilder(): UriBuilder = UriBuilder.fromUri(absolutePath)

    fun asKeycloakUriInfo(): KeycloakUriInfo =
        mockk {
            every { absolutePath } answers { this@TestUriInfo.absolutePath }
            every { queryParameters } answers { this@TestUriInfo.queryParameters }
            every { pathParameters } answers { this@TestUriInfo.pathParameters }
            every { baseUriBuilder } answers { this@TestUriInfo.baseUriBuilder }
        }
}

package no.fintlabs.keycloak.scim.application.endpoints

import no.fintlabs.keycloak.scim.endpoints.ScimServiceProviderConfigEndpoint
import no.fintlabs.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class ScimServiceProviderConfigEndpointTest {
    private val endpoint = ScimServiceProviderConfigEndpoint()
    private var uriInfo = TestUriInfo(URI("http://localhost/scim/v2/ServiceProviderConfig"))

    @Test
    fun `serviceProviderConfig returns expected capabilities`() {
        val config = endpoint.serviceProviderConfig()
        val scheme = config.authenticationSchemes[0]

        assertTrue(config.patch.isSupported)
        assertFalse(config.bulk.isSupported)
        assertEquals(1000, config.bulk.maxOperations)
        assertEquals(1048567, config.bulk.maxPayloadSize)
        assertTrue(config.filter.isSupported)
        assertEquals(200, config.filter.maxResults)
        assertFalse(config.changePassword.isSupported)
        assertTrue(config.sort.isSupported)
        assertTrue(config.etag.isSupported)
        assertEquals(1, config.authenticationSchemes.size)

        assertEquals("OAuth Bearer Token", scheme.name)
        assertEquals(
            "Authentication scheme using the OAuth Bearer Token Standard",
            scheme.description,
        )
        assertEquals(
            "https://www.rfc-editor.org/info/rfc6750",
            scheme.specUri.toString(),
        )
        assertEquals("oauthbearertoken", scheme.type)
        assertTrue(scheme.isPrimary)
    }

    @Test
    fun `get returns GenericScimResource with config and meta populated`() {
        val node = endpoint.get(uriInfo).objectNode

        val patchNode = node["patch"]
        assertTrue(patchNode["supported"].asBoolean())

        val bulkNode = node["bulk"]
        assertFalse(bulkNode["supported"].asBoolean())
        assertEquals(1000, bulkNode["maxOperations"].asInt())
        assertEquals(1048567, bulkNode["maxPayloadSize"].asInt())

        val filterNode = node["filter"]
        assertTrue(filterNode["supported"].asBoolean())
        assertEquals(200, filterNode["maxResults"].asInt())

        val changePasswordNode = node["changePassword"]
        assertFalse(changePasswordNode["supported"].asBoolean())

        val sortNode = node["sort"]
        assertTrue(sortNode["supported"].asBoolean())

        val etagNode = node["eTag"]
        assertTrue(etagNode["supported"].asBoolean())

        val authSchemes = node["authenticationSchemes"]
        assertTrue(authSchemes.isArray)
        assertEquals(1, authSchemes.size())

        val scheme = authSchemes[0]
        assertEquals("OAuth Bearer Token", scheme["name"].asText())
        assertEquals(
            "Authentication scheme using the OAuth Bearer Token Standard",
            scheme["description"].asText(),
        )
        assertEquals("https://www.rfc-editor.org/info/rfc6750", scheme["specUri"].asText())
        assertEquals("oauthbearertoken", scheme["type"].asText())
        assertTrue(scheme["primary"].asBoolean())

        val meta = node["meta"]
        assertEquals("ServiceProviderConfig", meta["resourceType"].asText())
    }
}

package no.fintlabs.keycloak.scim.application.endpoints

import com.fasterxml.jackson.databind.node.ArrayNode
import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.exceptions.ForbiddenException
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.messages.ListResponse
import no.fintlabs.keycloak.scim.endpoints.ScimSchemaEndpoint
import no.fintlabs.keycloak.scim.endpoints.ScimUserEndpoint
import no.fintlabs.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import java.net.URI

class ScimSchemaEndpointTest {
    private var uriInfo = TestUriInfo(URI("http://localhost/scim/v2/Schemas"))
    private val endpoint = ScimSchemaEndpoint(listOf(ScimUserEndpoint::class))

    @Test
    fun `search throws ForbiddenException when filter is provided`() {
        assertThrows<ForbiddenException> {
            endpoint.search(uriInfo, "id eq \"test\"")
        }
    }

    @Test
    fun `search returns schemas and user schema contains externalId attribute`() {
        val response: ListResponse<GenericScimResource> = endpoint.search(uriInfo, null)

        val userSchema =
            response.resources.firstOrNull {
                it.objectNode["id"].asText() == "urn:ietf:params:scim:schemas:core:2.0:User"
            }
        assertNotNull(userSchema)

        val attributesNode = userSchema.objectNode["attributes"] as ArrayNode
        val externalAttr =
            attributesNode.firstOrNull { attr ->
                attr["name"].asText() == "externalId"
            }
        assertNotNull(externalAttr)

        assertEquals("string", externalAttr["type"].asText())
        assertEquals(false, externalAttr["multiValued"].asBoolean())
        assertEquals("Identifier for the User as defined by the client.", externalAttr["description"].asText())
        assertEquals(false, externalAttr["required"].asBoolean())
        assertEquals(true, externalAttr["caseExact"].asBoolean())
        assertEquals("readWrite", externalAttr["mutability"].asText())
        assertEquals("default", externalAttr["returned"].asText())
        assertEquals("none", externalAttr["uniqueness"].asText())
    }

    @Test
    fun `get returns schema when requested by id`() {
        val id = "urn:ietf:params:scim:schemas:core:2.0:User"
        val node = endpoint.get(id, uriInfo).objectNode

        assertEquals(id, node["id"].asText())
        assertEquals("User", node["name"].asText())
    }

    @Test
    fun `get returns schema when requested by name`() {
        val node = endpoint.get("User", uriInfo).objectNode

        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", node["id"].asText())
        assertEquals("User", node["name"].asText())
    }

    @Test
    fun `get throws ResourceNotFoundException when schema does not exist`() {
        assertThrows<ResourceNotFoundException> {
            endpoint.get("does-not-exist", uriInfo)
        }
    }
}

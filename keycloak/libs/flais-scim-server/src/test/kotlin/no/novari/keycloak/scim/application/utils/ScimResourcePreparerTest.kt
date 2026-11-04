package no.novari.keycloak.scim.application.utils

import com.unboundid.scim2.common.ScimResource
import com.unboundid.scim2.common.types.Meta
import jakarta.ws.rs.core.MultivaluedHashMap
import no.novari.keycloak.scim.endpoints.ScimUserEndpoint
import no.novari.keycloak.scim.resources.UserResource
import no.novari.keycloak.scim.utils.ResourceTypeDefinitionUtil.createResourceTypeDefinition
import no.novari.keycloak.scim.utils.ScimResourcePreparer
import no.novari.keycloak.scim.utils.TestUriInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.util.Calendar

class ScimResourcePreparerTest {
    private val usersUrl = "https://example.test/realms/external/scim/v2/org-id/Users"
    private val resourceType = createResourceTypeDefinition<ScimUserEndpoint>()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `initializes metadata on typed and generic user resources`(generic: Boolean) {
        val user = UserResource().apply { id = "alice" }
        val resource: ScimResource = if (generic) user.asGenericScimResource() else user

        preparer().setResourceTypeAndLocation(resource)

        assertMeta(resource, "User", "$usersUrl/alice")
    }

    @ParameterizedTest
    @CsvSource(
        "Users, alice, false",
        "Users/, alice, false",
        "Users/alice, alice, true",
        "Users/alice/, alice, true",
        "Users/Users, Users, true",
    )
    fun `uses canonical collection and item locations`(
        requestPath: String,
        id: String,
        itemRequest: Boolean,
    ) {
        val resource = UserResource().apply { this.id = id }
        val uri = URI("${usersUrl.substringBeforeLast('/')}/$requestPath")
        val params =
            MultivaluedHashMap<String, String>().apply {
                if (itemRequest) add("id", id)
            }

        preparer(TestUriInfo(uri, pathParams = params)).setResourceTypeAndLocation(resource)

        assertMeta(resource, "User", "$usersUrl/$id")
    }

    @Test
    fun `uses absolute path without query parameters`() {
        val resource = UserResource().apply { id = "alice" }

        preparer(TestUriInfo(URI("$usersUrl?filter=userName&count=1")))
            .setResourceTypeAndLocation(resource)

        assertMeta(resource, "User", "$usersUrl/alice")
    }

    @Test
    fun `uses collection location when resource has no id`() {
        val resource = UserResource()

        preparer().setResourceTypeAndLocation(resource)

        assertMeta(resource, "User", usersUrl)
    }

    @Test
    fun `encodes template characters and reserved characters as a single id segment`() {
        val resource = UserResource().apply { id = "{user} /?#" }

        preparer().setResourceTypeAndLocation(resource)

        assertMeta(resource, "User", "$usersUrl/%7Buser%7D%20%2F%3F%23")
    }

    @ParameterizedTest
    @CsvSource(
        "false, false",
        "true, false",
        "false, true",
        "true, true",
    )
    fun `fills only missing metadata and preserves unrelated fields`(
        existingType: Boolean,
        existingLocation: Boolean,
    ) {
        val created = Calendar.getInstance().apply { timeInMillis = 1_000 }
        val modified = Calendar.getInstance().apply { timeInMillis = 2_000 }
        val resource =
            UserResource().apply {
                id = "alice"
                meta =
                    Meta().apply {
                        if (existingType) resourceType = "ExistingType"
                        if (existingLocation) location = URI("https://existing.test/user")
                        version = "existing-version"
                        this.created = created
                        lastModified = modified
                    }
            }
        val preparer = preparer()

        preparer.setResourceTypeAndLocation(resource)
        preparer.setResourceTypeAndLocation(resource)

        assertMeta(
            resource,
            if (existingType) "ExistingType" else "User",
            if (existingLocation) "https://existing.test/user" else "$usersUrl/alice",
        )
        assertEquals("existing-version", resource.meta.version)
        assertEquals(created, resource.meta.created)
        assertEquals(modified, resource.meta.lastModified)
    }

    private fun preparer(uriInfo: TestUriInfo = TestUriInfo(URI(usersUrl))) = ScimResourcePreparer<ScimResource>(resourceType, uriInfo)

    private fun assertMeta(
        resource: ScimResource,
        type: String,
        location: String,
    ) {
        val meta = resource.asGenericScimResource().objectNode["meta"]
        assertEquals(type, meta["resourceType"].asText())
        assertEquals(location, meta["location"].asText())
    }
}

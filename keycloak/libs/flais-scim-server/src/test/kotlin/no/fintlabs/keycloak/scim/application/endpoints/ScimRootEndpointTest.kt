package no.fintlabs.keycloak.scim.application.endpoints

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.ws.rs.Path
import no.fintlabs.keycloak.scim.context.ScimContext
import no.fintlabs.keycloak.scim.endpoints.ScimRootEndpoint
import no.fintlabs.keycloak.scim.endpoints.ScimUserEndpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScimRootEndpointTest {
    @MockK(relaxed = true)
    lateinit var scimContext: ScimContext

    lateinit var root: ScimRootEndpoint

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        root = ScimRootEndpoint(scimContext)
    }

    @Test
    fun `resourceClasses contains ScimUserEndpoint`() {
        val classes = root.resourceClasses

        assertEquals(1, classes.size)
        assertEquals(ScimUserEndpoint::class, classes.first())
    }

    @Test
    fun `users method has Users path annotation`() {
        val method = ScimRootEndpoint::class.java.getMethod("users")
        val path = method.getAnnotation(Path::class.java)

        assertNotNull(path)
        assertEquals("Users", path.value)
    }

    @Test
    fun `schemas method has Schemas path annotation`() {
        val method = ScimRootEndpoint::class.java.getMethod("schemas")
        val path = method.getAnnotation(Path::class.java)

        assertNotNull(path)
        assertEquals("Schemas", path.value)
    }

    @Test
    fun `serviceProviderConfig method has ServiceProviderConfig path annotation`() {
        val method = ScimRootEndpoint::class.java.getMethod("serviceProviderConfig")
        val path = method.getAnnotation(Path::class.java)

        assertNotNull(path)
        assertEquals("ServiceProviderConfig", path.value)
    }

    @Test
    fun `resourceTypes method has ResourceTypes path annotation`() {
        val method = ScimRootEndpoint::class.java.getMethod("resourceTypes")
        val path = method.getAnnotation(Path::class.java)

        assertNotNull(path)
        assertEquals("ResourceTypes", path.value)
    }

    @Test
    fun `users returns ScimUserEndpoint instance`() {
        val usersEndpoint = root.users()

        assertNotNull(usersEndpoint)
    }

    @Test
    fun `schemas returns ScimSchemaEndpoint instance`() {
        val schemasEndpoint = root.schemas()

        assertNotNull(schemasEndpoint)
    }

    @Test
    fun `serviceProviderConfig returns ScimServiceProviderConfigEndpoint instance`() {
        val spcEndpoint = root.serviceProviderConfig()

        assertNotNull(spcEndpoint)
    }

    @Test
    fun `resourceTypes returns ScimResourceTypesEndpoint instance`() {
        val resourceTypesEndpoint = root.resourceTypes()

        assertNotNull(resourceTypesEndpoint)
    }
}

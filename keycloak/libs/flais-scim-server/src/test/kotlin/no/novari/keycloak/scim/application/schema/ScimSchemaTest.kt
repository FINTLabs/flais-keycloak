package no.novari.keycloak.scim.application.schema

import com.unboundid.scim2.common.annotations.Schema
import no.novari.keycloak.scim.resources.FintUserExtension
import no.novari.keycloak.scim.resources.UserResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScimSchemaTest {
    @Test
    fun `user resource declares core schema urn`() {
        assertEquals("urn:ietf:params:scim:schemas:core:2.0:User", schemaId(UserResource::class.java))
    }

    @Test
    fun `fint extension declares extension schema urn`() {
        assertEquals("urn:ietf:params:scim:schemas:extension:fint:2.0:User", schemaId(FintUserExtension::class.java))
    }

    private fun schemaId(type: Class<*>): String = requireNotNull(type.getAnnotation(Schema::class.java)).id
}

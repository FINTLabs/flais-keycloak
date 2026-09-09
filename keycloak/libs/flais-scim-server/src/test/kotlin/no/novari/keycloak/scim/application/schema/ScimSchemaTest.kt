package no.novari.keycloak.scim.application.schema

import com.unboundid.scim2.common.annotations.Schema
import no.novari.keycloak.scim.resources.UserResource
import no.novari.keycloak.scim.schema.FintUserExtensionSchema
import no.novari.keycloak.scim.schema.UserResourceSchema
import no.novari.keycloak.scim.types.FintUserExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScimSchemaTest {
    @Test
    fun `user resource mapping uses urn from schema annotation`() {
        assertEquals(schemaId(UserResource::class.java), UserResourceSchema.urn)
    }

    @Test
    fun `fint extension mapping uses urn from schema annotation`() {
        assertEquals(schemaId(FintUserExtension::class.java), FintUserExtensionSchema.urn)
    }

    @Test
    fun `complex user paths are derived from resource and child type accessors`() {
        assertEquals("emails.value", UserResourceSchema.Emails.value.value)
        assertEquals("emails.primary", UserResourceSchema.Emails.primary.value)
        assertEquals("roles.display", UserResourceSchema.Roles.display.value)
        assertEquals("roles.type", UserResourceSchema.Roles.type.value)
    }

    private fun schemaId(type: Class<*>): String = requireNotNull(type.getAnnotation(Schema::class.java)).id
}

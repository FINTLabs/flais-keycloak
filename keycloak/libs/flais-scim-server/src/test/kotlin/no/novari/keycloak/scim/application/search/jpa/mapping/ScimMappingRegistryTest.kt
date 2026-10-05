package no.novari.keycloak.scim.application.search.jpa.mapping

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.annotations.Schema
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import no.novari.keycloak.scim.search.jpa.mapping.Column
import no.novari.keycloak.scim.search.jpa.mapping.Constant
import no.novari.keycloak.scim.search.jpa.mapping.ConstantValue
import no.novari.keycloak.scim.search.jpa.mapping.FieldKind
import no.novari.keycloak.scim.search.jpa.mapping.ScimMapping
import no.novari.keycloak.scim.search.jpa.mapping.ScimMappingRegistry
import no.novari.keycloak.scim.search.jpa.mapping.column
import no.novari.keycloak.scim.search.jpa.mapping.constant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import jakarta.ws.rs.Path as JaxRsPath

internal class ScimMappingRegistryTest {
    private object TestEntity

    @ResourceType(
        description = "Test resource",
        name = "Test",
        schema = TestSchema::class,
    )
    @JaxRsPath("Test")
    private class TestResource

    @Schema(id = "urn:test:schemas:core", name = "TestSchema", description = "Test schema")
    private class TestSchema {
        var userName: String? = null
        var emails: List<TestEmail>? = null
    }

    private class TestEmail {
        var value: String? = null
        var primary: Boolean? = null
    }

    private object TestEmailMapping : ScimMapping<TestEmail, TestEntity>(TestEmail::class) {
        init {
            property(TestEmail::value, column<TestEntity>("email", FieldKind.STORED_LOWERCASE))
            property(TestEmail::primary, constant<TestEntity>(true))
        }
    }

    @Schema(id = "urn:test:schemas:core", name = "TestSchema", description = "Test schema")
    private object TestMapping : ScimMapping<TestSchema, TestEntity>(TestSchema::class) {
        init {
            property(TestSchema::userName, column<TestEntity>("username", FieldKind.STORED_LOWERCASE))
            complexCollection(TestSchema::emails, column<TestEntity>("email", FieldKind.STORED_LOWERCASE), TestEmailMapping)
        }
    }

    private val registry =
        ScimMappingRegistry.create<TestEntity>(
            resourceTypeDefinition = ResourceTypeDefinition.fromJaxRsResource(TestResource::class.java),
            configurators = listOf(TestMapping),
        )

    @Test
    fun `paths without schema urn resolve against default schema`() {
        assertEquals(
            Column<TestEntity>("username", FieldKind.STORED_LOWERCASE),
            registry.resolve(Path.fromString("userName")),
        )
    }

    @Test
    fun `schema urn matching is case insensitive`() {
        assertEquals(
            Column<TestEntity>("username", FieldKind.STORED_LOWERCASE),
            registry.resolve(Path.fromString("URN:TEST:SCHEMAS:CORE:userName")),
        )
    }

    @Test
    fun `registry validates that resource core schema has a mapping`() {
        assertThrows<IllegalArgumentException> {
            ScimMappingRegistry.create<TestEntity>(
                resourceTypeDefinition = ResourceTypeDefinition.fromJaxRsResource(TestResource::class.java),
                configurators = emptyList(),
            )
        }
    }

    @Test
    fun `nested paths resolve against complex mappings`() {
        assertEquals(
            Column<TestEntity>("email", FieldKind.STORED_LOWERCASE),
            registry.resolve(Path.fromString("emails.value")),
        )
        assertEquals(
            Constant<TestEntity>(ConstantValue.Bool(true), FieldKind.BOOLEAN),
            registry.resolve(Path.fromString("emails.primary")),
        )
    }

    @Test
    fun `value filtered paths are unresolved`() {
        assertNull(registry.resolve(Path.fromString("emails[primary eq true].value")))
        assertNull(registry.resolve(Path.fromString("emails[primary eq false].value")))
        assertNull(registry.resolve(Path.fromString("""emails[value eq "a@example.no"].value""")))
    }
}

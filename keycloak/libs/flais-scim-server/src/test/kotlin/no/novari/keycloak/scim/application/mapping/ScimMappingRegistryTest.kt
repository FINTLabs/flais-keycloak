package no.novari.keycloak.scim.application.mapping

import com.unboundid.scim2.common.Path
import no.novari.keycloak.scim.mapping.Column
import no.novari.keycloak.scim.mapping.FieldKind
import no.novari.keycloak.scim.mapping.ScimFieldPath
import no.novari.keycloak.scim.mapping.ScimMappingEntry
import no.novari.keycloak.scim.mapping.ScimMappingModule
import no.novari.keycloak.scim.mapping.ScimMappingRegistry
import no.novari.keycloak.scim.mapping.column
import no.novari.keycloak.scim.mapping.constant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ScimMappingRegistryTest {
    private object TestEntity

    private object TestModule : ScimMappingModule<TestEntity> {
        override val schemaUrn = "urn:test:schemas:core"

        override val entries =
            listOf(
                ScimMappingEntry(ScimFieldPath(schemaUrn, "userName"), column<TestEntity>("username", FieldKind.STORED_LOWERCASE)),
                ScimMappingEntry(ScimFieldPath(schemaUrn, "emails.value"), column<TestEntity>("email", FieldKind.STORED_LOWERCASE)),
                ScimMappingEntry(ScimFieldPath(schemaUrn, "emails.primary"), constant<TestEntity>(true)),
            )
    }

    private val registry = ScimMappingRegistry(listOf(TestModule), defaultSchemaUrn = TestModule.schemaUrn)

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
    fun `paths without schema urn do not resolve when no default schema is configured`() {
        val registryWithoutDefault = ScimMappingRegistry(listOf(TestModule), defaultSchemaUrn = null)

        assertNull(registryWithoutDefault.resolve(Path.fromString("userName")))
    }

    @Test
    fun `value filters matching boolean constants are collapsed`() {
        assertEquals(
            Column<TestEntity>("email", FieldKind.STORED_LOWERCASE),
            registry.resolve(Path.fromString("emails[primary eq true].value")),
        )
    }

    @Test
    fun `value filters that do not match boolean constants are unresolved`() {
        assertNull(registry.resolve(Path.fromString("emails[primary eq false].value")))
        assertNull(registry.resolve(Path.fromString("""emails[value eq "a@example.no"].value""")))
    }
}

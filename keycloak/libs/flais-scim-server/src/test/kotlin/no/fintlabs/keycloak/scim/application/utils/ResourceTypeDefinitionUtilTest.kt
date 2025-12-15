package no.fintlabs.keycloak.scim.application.utils

import com.unboundid.scim2.common.types.EnterpriseUserExtension
import com.unboundid.scim2.common.types.SchemaResource
import com.unboundid.scim2.common.utils.SchemaUtils
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import no.fintlabs.keycloak.scim.utils.ResourcePath
import no.fintlabs.keycloak.scim.utils.ResourceTypeDefinitionUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceTypeDefinitionUtilTest {
    @ResourceType(
        description = "Test resource type",
        name = "TestResource",
        schema = SchemaResource::class,
        discoverable = true,
        optionalSchemaExtensions = [EnterpriseUserExtension::class],
        requiredSchemaExtensions = [],
    )
    @ResourcePath("TestPath")
    class AnnotatedResource

    @ResourcePath("OnlyPath")
    class MissingResourceType

    @ResourceType(
        description = "Has ResourceType but no ResourcePath",
        name = "NoPathResource",
        schema = SchemaResource::class,
        discoverable = true,
    )
    class MissingResourcePath

    @Test
    fun `createResourceTypeDefinition builds definition from annotations`() {
        val def: ResourceTypeDefinition =
            ResourceTypeDefinitionUtil.createResourceTypeDefinition(AnnotatedResource::class)

        assertEquals("TestResource", def.name)
        assertEquals("TestPath", def.endpoint)
        assertEquals("Test resource type", def.description)
        assertTrue(def.isDiscoverable)

        val expectedCoreSchema = SchemaUtils.getSchema(SchemaResource::class.java)
        val coreSchema = def.coreSchema
        assertNotNull(coreSchema)
        assertEquals(expectedCoreSchema.id, coreSchema!!.id)
        assertEquals(expectedCoreSchema.name, coreSchema.name)
    }

    @Test
    fun `createResourceTypeDefinition throws when ResourceType annotation is missing`() {
        val ex =
            assertThrows<IllegalStateException> {
                ResourceTypeDefinitionUtil.createResourceTypeDefinition(MissingResourceType::class)
            }
        assertTrue(
            ex.message!!.contains("Missing @ResourceType annotation"),
        )
        assertTrue(
            ex.message!!.contains(MissingResourceType::class.qualifiedName!!),
        )
    }

    @Test
    fun `createResourceTypeDefinition throws when ResourcePath annotation is missing`() {
        val ex =
            assertThrows<IllegalStateException> {
                ResourceTypeDefinitionUtil.createResourceTypeDefinition(MissingResourcePath::class)
            }
        assertTrue(
            ex.message!!.contains("Missing @ResourcePath annotation"),
        )
        assertTrue(
            ex.message!!.contains(MissingResourcePath::class.qualifiedName!!),
        )
    }

    @Test
    fun `inline createResourceTypeDefinition uses KClass overload`() {
        val def: ResourceTypeDefinition =
            ResourceTypeDefinitionUtil.createResourceTypeDefinition(AnnotatedResource::class)

        assertEquals("TestResource", def.name)
        assertEquals("TestPath", def.endpoint)
    }
}

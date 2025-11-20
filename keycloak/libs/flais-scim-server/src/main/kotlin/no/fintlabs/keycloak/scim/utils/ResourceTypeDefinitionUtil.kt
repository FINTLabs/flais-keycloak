package no.fintlabs.keycloak.scim.utils

import com.unboundid.scim2.common.utils.SchemaUtils
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import kotlin.reflect.KClass

object ResourceTypeDefinitionUtil {
    fun createResourceTypeDefinition(resource: KClass<*>): ResourceTypeDefinition {
        val resourceType =
            resource.java.getAnnotation(ResourceType::class.java)
                ?: error("Missing @ResourceType annotation on ${resource.qualifiedName}")

        val path =
            resource.java.getAnnotation(ResourcePath::class.java)
                ?: error("Missing @ResourcePath annotation on ${resource.qualifiedName}")

        return ResourceTypeDefinition
            .Builder(resourceType.name, path.value)
            .setDescription(resourceType.description)
            .setCoreSchema(SchemaUtils.getSchema(resourceType.schema.java))
            .setDiscoverable(resourceType.discoverable)
            .apply {
                resourceType.optionalSchemaExtensions.forEach {
                    addOptionalSchemaExtension(SchemaUtils.getSchema(it.java))
                }
                resourceType.requiredSchemaExtensions.forEach {
                    addRequiredSchemaExtension(SchemaUtils.getSchema(it.java))
                }
            }.build()
    }

    inline fun <reified T : Any> createResourceTypeDefinition() = createResourceTypeDefinition(T::class)
}

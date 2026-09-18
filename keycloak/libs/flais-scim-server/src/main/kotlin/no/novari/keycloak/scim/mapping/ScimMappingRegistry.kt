package no.novari.keycloak.scim.mapping

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.annotations.Schema
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import java.util.Locale
import kotlin.reflect.full.findAnnotation

internal class ScimMappingRegistry<E : Any> private constructor(
    private val defaultSchemaUrn: String,
    private val tables: Map<String, Map<String, FieldMapping<E>>>,
) {
    fun resolve(path: Path): FieldMapping<E>? {
        val urn =
            (path.schemaUrn ?: defaultSchemaUrn)
                .normalizedUrn()

        val table =
            tables[urn]
                ?: return null

        val key =
            path.normalizedPath()
                ?: return null

        return table[key]
    }

    private fun Path.normalizedPath(): String? =
        buildString {
            this@normalizedPath.forEachIndexed { index, element ->
                // MappingRegistry only resolves fields.
                // Complex value filters are handled by the filter compiler.
                if (element.valueFilter != null) {
                    return null
                }

                if (index > 0) {
                    append('.')
                }

                append(
                    element.attribute.lowercase(Locale.ROOT),
                )
            }
        }

    private inline fun Path.forEachIndexed(action: (Int, Path.Element) -> Unit) {
        var index = 0

        for (element in this) {
            action(index++, element)
        }
    }

    private fun String.normalizedUrn(): String = lowercase(Locale.ROOT)

    companion object {
        fun <E : Any> create(
            resourceTypeDefinition: ResourceTypeDefinition,
            configurators: List<ScimMapping<*, E>>,
        ): ScimMappingRegistry<E> {
            val configuredSchemas =
                configurators.map { configurator ->
                    configurator.validate()
                    val schema = requireNotNull(configurator.type.findAnnotation<Schema>())
                    RegisteredSchema(
                        urn = schema.id,
                        mappings = configurator.mappings.toMap(),
                    )
                }

            validateSchemas(
                definition = resourceTypeDefinition,
                configured = configuredSchemas,
            )

            return ScimMappingRegistry(
                defaultSchemaUrn =
                    resourceTypeDefinition.coreSchema.id.normalizedUrn(),
                tables =
                    configuredSchemas.associate { schema ->
                        schema.urn.normalizedUrn() to
                            schema.mappings
                    },
            )
        }

        private fun <E : Any> validateSchemas(
            definition: ResourceTypeDefinition,
            configured: Collection<RegisteredSchema<E>>,
        ) {
            val configuredUrns =
                configured
                    .map { it.urn.normalizedUrn() }

            require(
                configuredUrns.size ==
                    configuredUrns.toSet().size,
            ) {
                "Duplicate SCIM schema mappings configured"
            }

            val expectedUrns =
                listOf(definition.coreSchema.id)
                    .plus(definition.schemaExtensions.map { it.key.id })
                    .map { it.normalizedUrn() }
                    .toSet()

            val actualUrns =
                configuredUrns.toSet()

            val missing =
                expectedUrns - actualUrns

            require(missing.isEmpty()) {
                "Missing SCIM search mappings for: " +
                    missing.joinToString()
            }

            val unexpected =
                actualUrns - expectedUrns

            require(unexpected.isEmpty()) {
                "SCIM search mappings configured for schemas not exposed " +
                    "by the resource: ${unexpected.joinToString()}"
            }
        }

        private fun String.normalizedUrn(): String = lowercase(Locale.ROOT)
    }

    private data class RegisteredSchema<E : Any>(
        val urn: String,
        val mappings: Map<String, FieldMapping<E>>,
    )
}

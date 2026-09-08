package no.novari.keycloak.scim.mapping

internal data class ScimMappingEntry<E : Any>(
    val path: ScimFieldPath,
    val mapping: FieldMapping<E>,
)

internal interface ScimMappingModule<E : Any> {
    val schemaUrn: String?
    val entries: List<ScimMappingEntry<E>>
}

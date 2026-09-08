package no.novari.keycloak.scim.mapping

internal data class ScimFieldPath(
    val schemaUrn: String?,
    val value: String,
) {
    fun normalized(): String = value.lowercase()
}

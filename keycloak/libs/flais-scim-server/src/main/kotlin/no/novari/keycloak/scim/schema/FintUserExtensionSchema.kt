package no.novari.keycloak.scim.schema

import no.novari.keycloak.scim.types.FintUserExtension

internal object FintUserExtensionSchema {
    private val schema = ScimSchema(FintUserExtension::class)
    val urn = schema.urn

    val employeeId = schema.path(FintUserExtension::employeeId)
    val familyName = schema.path(FintUserExtension::familyName)
    val givenName = schema.path(FintUserExtension::givenName)
    val studentNumber = schema.path(FintUserExtension::studentNumber)
    val userPrincipalName = schema.path(FintUserExtension::userPrincipalName)
}

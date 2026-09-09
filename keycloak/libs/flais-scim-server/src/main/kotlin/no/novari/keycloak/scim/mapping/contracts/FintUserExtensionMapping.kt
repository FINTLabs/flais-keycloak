package no.novari.keycloak.scim.mapping.contracts

import no.novari.keycloak.scim.mapping.FieldMapping
import no.novari.keycloak.scim.mapping.ScimMappingEntry
import no.novari.keycloak.scim.mapping.ScimMappingModule
import no.novari.keycloak.scim.schema.FintUserExtensionSchema

internal interface FintUserExtensionMapping<E : Any> {
    val employeeId: FieldMapping<E>
    val familyName: FieldMapping<E>
    val givenName: FieldMapping<E>
    val studentNumber: FieldMapping<E>
    val userPrincipalName: FieldMapping<E>
}

internal class FintUserExtensionMappingModule<E : Any>(
    private val mapping: FintUserExtensionMapping<E>,
) : ScimMappingModule<E> {
    override val schemaUrn: String = FintUserExtensionSchema.urn

    override val entries =
        listOf(
            ScimMappingEntry(FintUserExtensionSchema.employeeId, mapping.employeeId),
            ScimMappingEntry(FintUserExtensionSchema.familyName, mapping.familyName),
            ScimMappingEntry(FintUserExtensionSchema.givenName, mapping.givenName),
            ScimMappingEntry(FintUserExtensionSchema.studentNumber, mapping.studentNumber),
            ScimMappingEntry(FintUserExtensionSchema.userPrincipalName, mapping.userPrincipalName),
        )
}

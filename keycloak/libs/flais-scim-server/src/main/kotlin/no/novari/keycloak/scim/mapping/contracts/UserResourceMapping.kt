package no.novari.keycloak.scim.mapping.contracts

import no.novari.keycloak.scim.mapping.FieldMapping
import no.novari.keycloak.scim.mapping.ScimMappingEntry
import no.novari.keycloak.scim.mapping.ScimMappingModule
import no.novari.keycloak.scim.schema.UserResourceSchema

internal interface EmailMapping<E : Any> {
    val self: FieldMapping<E>
    val value: FieldMapping<E>
    val primary: FieldMapping<E>
    val type: FieldMapping<E>
}

internal interface RoleMapping<E : Any> {
    val self: FieldMapping<E>
    val value: FieldMapping<E>
    val display: FieldMapping<E>
    val type: FieldMapping<E>
    val primary: FieldMapping<E>
}

internal interface UserResourceMapping<E : Any> {
    val id: FieldMapping<E>
    val externalId: FieldMapping<E>
    val active: FieldMapping<E>
    val emails: EmailMapping<E>
    val roles: RoleMapping<E>
    val userName: FieldMapping<E>
}

internal class UserResourceMappingModule<E : Any>(
    private val mapping: UserResourceMapping<E>,
) : ScimMappingModule<E> {
    override val schemaUrn: String = UserResourceSchema.urn

    override val entries =
        listOf(
            ScimMappingEntry(UserResourceSchema.id, mapping.id),
            ScimMappingEntry(UserResourceSchema.externalId, mapping.externalId),
            ScimMappingEntry(UserResourceSchema.active, mapping.active),
            ScimMappingEntry(UserResourceSchema.Emails.self, mapping.emails.self),
            ScimMappingEntry(UserResourceSchema.Emails.value, mapping.emails.value),
            ScimMappingEntry(UserResourceSchema.Emails.primary, mapping.emails.primary),
            ScimMappingEntry(UserResourceSchema.Emails.type, mapping.emails.type),
            ScimMappingEntry(UserResourceSchema.Roles.self, mapping.roles.self),
            ScimMappingEntry(UserResourceSchema.Roles.value, mapping.roles.value),
            ScimMappingEntry(UserResourceSchema.Roles.display, mapping.roles.display),
            ScimMappingEntry(UserResourceSchema.Roles.type, mapping.roles.type),
            ScimMappingEntry(UserResourceSchema.Roles.primary, mapping.roles.primary),
            ScimMappingEntry(UserResourceSchema.userName, mapping.userName),
        )
}

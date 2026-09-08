package no.novari.keycloak.scim.mapping.keycloak

import no.novari.keycloak.scim.mapping.FieldKind
import no.novari.keycloak.scim.mapping.alwaysPresentComplex
import no.novari.keycloak.scim.mapping.attribute
import no.novari.keycloak.scim.mapping.column
import no.novari.keycloak.scim.mapping.constant
import no.novari.keycloak.scim.mapping.contracts.EmailMapping
import no.novari.keycloak.scim.mapping.contracts.FintUserExtensionMapping
import no.novari.keycloak.scim.mapping.contracts.RoleMapping
import no.novari.keycloak.scim.mapping.contracts.UserResourceMapping
import no.novari.keycloak.scim.mapping.unsupported
import org.keycloak.models.jpa.entities.UserEntity

internal object KeycloakUserResourceMapping : UserResourceMapping<UserEntity> {
    override val id = column<UserEntity>("id", FieldKind.CASE_EXACT)
    override val externalId = attribute<UserEntity>("externalId", FieldKind.CASE_EXACT)
    override val userName = column<UserEntity>("username", FieldKind.STORED_LOWERCASE)
    override val active = column<UserEntity>("enabled", FieldKind.BOOLEAN)
    override val emails = KeycloakEmailMapping
    override val roles = KeycloakRoleMapping
}

internal object KeycloakEmailMapping : EmailMapping<UserEntity> {
    override val self = alwaysPresentComplex<UserEntity>("emails")
    override val value = column<UserEntity>("email", FieldKind.STORED_LOWERCASE)
    override val primary = constant<UserEntity>(true)
    override val type = unsupported<UserEntity>("emails.type is not emitted by this provider")
}

internal object KeycloakRoleMapping : RoleMapping<UserEntity> {
    override val self = attribute<UserEntity>("rawRoles", FieldKind.CASE_EXACT, multiValued = true)
    override val value = attribute<UserEntity>("roles", FieldKind.MIXED_CASE, multiValued = true)
    override val display = unsupported<UserEntity>("roles.display is only stored inside rawRoles JSON")
    override val type = unsupported<UserEntity>("roles.type is only stored inside rawRoles JSON")
    override val primary = unsupported<UserEntity>("roles.primary is only stored inside rawRoles JSON")
}

internal object KeycloakFintUserExtensionMapping : FintUserExtensionMapping<UserEntity> {
    override val givenName = column<UserEntity>("firstName", FieldKind.MIXED_CASE)
    override val familyName = column<UserEntity>("lastName", FieldKind.MIXED_CASE)
    override val employeeId = attribute<UserEntity>("employeeId", FieldKind.CASE_EXACT)
    override val studentNumber = attribute<UserEntity>("studentNumber", FieldKind.CASE_EXACT)
    override val userPrincipalName = attribute<UserEntity>("userPrincipalName", FieldKind.CASE_EXACT)
}

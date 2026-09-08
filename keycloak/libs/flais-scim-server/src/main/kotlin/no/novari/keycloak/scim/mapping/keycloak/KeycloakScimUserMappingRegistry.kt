package no.novari.keycloak.scim.mapping.keycloak

import com.unboundid.scim2.common.Path
import no.novari.keycloak.scim.mapping.Column
import no.novari.keycloak.scim.mapping.FieldMapping
import no.novari.keycloak.scim.mapping.ScimMappingRegistry
import no.novari.keycloak.scim.mapping.contracts.FintUserExtensionMappingModule
import no.novari.keycloak.scim.mapping.contracts.UserResourceMappingModule
import no.novari.keycloak.scim.schema.FintUserExtensionSchema
import no.novari.keycloak.scim.schema.UserResourceSchema
import org.keycloak.models.jpa.entities.UserEntity

/**
 * Maps SCIM attribute paths onto Keycloak user storage.
 *
 * The mapping must mirror `ScimUserEndpoint.translateUser`. Any attribute that cannot be mapped
 * exactly is left unresolved so the caller can reject it rather than answering from a subtly
 * different definition.
 *
 * Deliberately unsupported:
 * - `roles.type`, `roles.display`, `roles.primary` — only stored inside the `rawRoles` JSON blob.
 * - `emails.type` — never emitted by this provider.
 *
 * Deliberately absent:
 * - `meta.*` — not persisted as user data.
 */
internal object KeycloakScimUserMappingRegistry {
    val fintSchemaUrn = FintUserExtensionSchema.urn
    private val coreSchemaUrn = UserResourceSchema.urn

    private val registry =
        ScimMappingRegistry(
            modules =
                listOf(
                    UserResourceMappingModule(KeycloakUserResourceMapping),
                    FintUserExtensionMappingModule(KeycloakFintUserExtensionMapping),
                ),
            defaultSchemaUrn = coreSchemaUrn,
        )

    fun resolve(path: Path): FieldMapping<UserEntity>? = registry.resolve(path)

    /** Convenience for callers that only accept fields orderable by a single column. */
    fun resolveSortColumn(path: Path): Column<UserEntity>? = (resolve(path) as? Column<UserEntity>)?.takeIf { it.sortable }
}

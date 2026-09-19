package no.novari.keycloak.scim.search.jpa.mapping

import com.unboundid.scim2.common.types.Role
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimRoleMapping :
    ScimMapping<Role, UserEntity>(Role::class) {
    init {
        property(
            "value",
            attribute(
                "roles",
                FieldKind.MIXED_CASE,
                multiValued = true,
            ),
        )

        property(
            "display",
            unsupported(
                "Keycloak does not persist SCIM role display name separately",
            ),
        )

        property(
            "type",
            unsupported(
                "Keycloak does not persist SCIM role type separately",
            ),
        )

        property(
            "primary",
            unsupported(
                "Keycloak does not persist role primary separately",
            ),
        )
    }
}

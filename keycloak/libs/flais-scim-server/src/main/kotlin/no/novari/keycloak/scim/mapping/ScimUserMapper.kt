package no.novari.keycloak.scim.mapping

import no.novari.keycloak.scim.resources.UserResource
import org.keycloak.models.UserModel
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimUserMapper :
    ScimMapping<UserResource, UserEntity>(UserResource::class) {
    init {
        property(
            "id",
            column(
                UserModel.ID,
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            "externalId",
            attribute(
                "externalId", // TODO: Move to constant
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            UserResource::userName,
            column(
                UserModel.USERNAME,
                FieldKind.STORED_LOWERCASE,
            ),
        )

        property(
            UserResource::active,
            column(
                UserModel.ENABLED,
                FieldKind.BOOLEAN,
            ),
        )

        complexCollection(
            UserResource::roles,
            attribute(
                "roles",
                FieldKind.CASE_EXACT,
                multiValued = true,
            ),
            ScimRoleMapper,
        )

        complexCollection(
            UserResource::emails,
            alwaysPresentComplex("emails"),
            ScimEmailMapper,
        )
    }
}

package no.novari.keycloak.scim.search.jpa.mapping

import no.novari.keycloak.scim.resources.FintUserExtension
import org.keycloak.models.UserModel
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimFintUserMapping :
    ScimMapping<FintUserExtension, UserEntity>(FintUserExtension::class) {
    init {
        property(
            FintUserExtension::userPrincipalName,
            attribute(
                "userPrincipalName",
                FieldKind.MIXED_CASE,
            ),
        )

        property(
            FintUserExtension::givenName,
            column(
                UserModel.FIRST_NAME,
                FieldKind.MIXED_CASE,
            ),
        )

        property(
            FintUserExtension::familyName,
            column(
                UserModel.LAST_NAME,
                FieldKind.MIXED_CASE,
            ),
        )

        property(
            FintUserExtension::employeeId,
            attribute(
                "employeeId", // TODO: Move to constant
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            FintUserExtension::studentNumber,
            attribute(
                "studentNumber", // TODO: Move to constant
                FieldKind.CASE_EXACT,
            ),
        )
    }
}

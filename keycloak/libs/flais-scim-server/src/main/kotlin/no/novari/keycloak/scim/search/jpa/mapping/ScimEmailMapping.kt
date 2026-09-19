package no.novari.keycloak.scim.search.jpa.mapping

import com.unboundid.scim2.common.types.Email
import org.keycloak.models.UserModel
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimEmailMapping :
    ScimMapping<Email, UserEntity>(Email::class) {
    init {
        property(
            "value",
            column(
                UserModel.EMAIL,
                FieldKind.MIXED_CASE,
            ),
        )

        property(
            "display",
            unsupported(
                "Keycloak does not persist email display name separately",
            ),
        )

        property(
            "type",
            constant("work"),
        )

        property(
            "primary",
            constant(true),
        )
    }
}

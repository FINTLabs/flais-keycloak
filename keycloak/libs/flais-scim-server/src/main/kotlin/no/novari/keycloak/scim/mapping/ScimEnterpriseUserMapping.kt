package no.novari.keycloak.scim.mapping

import com.unboundid.scim2.common.types.EnterpriseUserExtension
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimEnterpriseUserMapping : ScimMapping<EnterpriseUserExtension, UserEntity>(EnterpriseUserExtension::class) {
    override val ignoreNotMapped: Boolean = true
}

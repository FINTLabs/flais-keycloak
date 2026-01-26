package no.fintlabs.utils

import org.keycloak.models.KeycloakSession
import org.keycloak.models.UserModel
import org.keycloak.organization.OrganizationProvider

object OrgAttributeUtils {
    fun getAttributeValue(
        session: KeycloakSession,
        user: UserModel,
        attributeName: String,
    ): String? {
        val orgProvider = session.getProvider(OrganizationProvider::class.java)

        return orgProvider
            .getByMember(user)
            .map { org -> org.attributes[attributeName] }
            .filter { !it.isNullOrEmpty() }
            .map { it!![0] }
            .findFirst()
            .orElse(null)
    }
}

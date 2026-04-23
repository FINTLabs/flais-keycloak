package no.novari.utils

import no.novari.attributes.ClientPermissionAttribute
import org.keycloak.models.ClientModel

object ClientPermissionAttributes {
    fun getWhitelistedOrganizations(client: ClientModel): List<String> =
        client.getCsvAttribute(ClientPermissionAttribute.PERMISSION_WHITELISTED_ORGANIZATIONS)

    fun getBlacklistedOrganizations(client: ClientModel): List<String> =
        client.getCsvAttribute(ClientPermissionAttribute.PERMISSION_BLACKLISTED_ORGANIZATIONS)

    private fun ClientModel.getCsvAttribute(key: String): List<String> =
        attributes[key]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}

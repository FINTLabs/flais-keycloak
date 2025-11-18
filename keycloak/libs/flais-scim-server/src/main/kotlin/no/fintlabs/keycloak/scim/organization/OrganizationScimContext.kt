package no.fintlabs.keycloak.scim.organization

import no.fintlabs.keycloak.scim.ScimContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import java.net.URI

/**
 * SCIM context for organizations
 */
class OrganizationScimContext(
    baseUri: URI,
    session: KeycloakSession,
    realm: RealmModel,
    private val organization: OrganizationModel,
    config: OrganizationScimConfig
) : ScimContext(baseUri, session, realm, config) {

    /**
     * Gets the organization
     *
     * @return organization
     */
    fun getOrganization(): OrganizationModel = organization
}

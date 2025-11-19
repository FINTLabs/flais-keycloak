package no.fintlabs.keycloak.scim

import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.organization.OrganizationProvider

/**
 * SCIM context
 */
class ScimContext(
    val session: KeycloakSession,
    val realm: RealmModel,
    val orgProvider: OrganizationProvider,
    val organization: OrganizationModel,
)

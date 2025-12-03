package no.fintlabs.keycloak.scim.context

import jakarta.ws.rs.NotFoundException
import no.fintlabs.keycloak.scim.config.OrganizationScimConfig
import no.fintlabs.keycloak.scim.config.ScimConfig
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.organization.OrganizationProvider

class ScimContext internal constructor(
    val config: ScimConfig,
    val session: KeycloakSession,
    val realm: RealmModel,
    val orgProvider: OrganizationProvider,
    val organization: OrganizationModel,
)

fun createScimContext(
    session: KeycloakSession,
    organizationId: String,
): ScimContext {
    val kcContext =
        requireNotNull(session.context) {
            "Keycloak context is not set"
        }

    val orgProvider = session.getProvider(OrganizationProvider::class.java)
    val organization =
        orgProvider.getById(organizationId)
            ?: throw NotFoundException("Organization not found")

    val config = OrganizationScimConfig(organization)
    config.validateConfig()

    return ScimContext(
        config,
        session,
        kcContext.realm,
        orgProvider,
        organization,
    )
}

package no.novari.keycloak.scim.context

import jakarta.ws.rs.NotFoundException
import no.novari.keycloak.scim.config.OrganizationScimConfig
import no.novari.keycloak.scim.config.ScimConfig
import org.jboss.logging.Logger
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

private val logger: Logger = Logger.getLogger(ScimContext::class.java)

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
    runCatching {
        config.validateConfig()
    }.onFailure {
        logger.warnf(it, "Invalid SCIM configuration. organizationId=%s", organizationId)
    }.getOrThrow()

    logger.debugf(
        "Resolved SCIM context. organizationId=%s",
        organizationId,
    )

    return ScimContext(
        config,
        session,
        kcContext.realm,
        orgProvider,
        organization,
    )
}

package no.fintlabs.keycloak.scim

import no.fintlabs.keycloak.scim.authentication.JwtValidatorRegistry
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.services.resource.RealmResourceProviderFactory

class ScimRealmResourceProviderFactory : RealmResourceProviderFactory {
    private val authValidatorRegistry = JwtValidatorRegistry()

    override fun create(session: KeycloakSession) = ScimRealmResourceProvider(session, authValidatorRegistry)

    override fun init(config: Config.Scope) = Unit

    override fun postInit(factory: KeycloakSessionFactory) = Unit

    override fun close() = Unit

    override fun getId(): String = "scim"
}

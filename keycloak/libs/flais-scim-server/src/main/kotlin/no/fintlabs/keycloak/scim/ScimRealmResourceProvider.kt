package no.fintlabs.keycloak.scim

import no.fintlabs.keycloak.scim.endpoints.ScimRootEndpoint
import org.keycloak.services.resource.RealmResourceProvider

class ScimRealmResourceProvider : RealmResourceProvider {
    override fun getResource(): Any = ScimRootEndpoint()

    override fun close() = Unit
}

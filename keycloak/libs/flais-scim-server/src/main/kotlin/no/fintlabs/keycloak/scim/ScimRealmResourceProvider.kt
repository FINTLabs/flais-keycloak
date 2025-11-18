package no.fintlabs.keycloak.scim

import org.keycloak.services.resource.RealmResourceProvider

class ScimRealmResourceProvider : RealmResourceProvider {
    override fun getResource(): Any = ScimRootResource()
    override fun close() = Unit
}

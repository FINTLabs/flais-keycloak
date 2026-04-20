package no.fintlabs.provider

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

class DefaultClientOrgAccessProviderFactory : ClientOrgAccessProviderFactory {
    private val providerId = "default-client-org-access-provider"

    override fun create(session: KeycloakSession): DefaultClientOrgAccessProvider = DefaultClientOrgAccessProvider(session)

    override fun init(config: Config.Scope) = Unit

    override fun postInit(factory: KeycloakSessionFactory) = Unit

    override fun close() = Unit

    override fun getId(): String = providerId
}

package no.novari.provider

import org.keycloak.Config
import org.keycloak.common.Profile
import org.keycloak.provider.EnvironmentDependentProviderFactory
import org.keycloak.provider.ProviderFactory

interface ClientOrgAccessProviderFactory :
    ProviderFactory<ClientOrgAccessProvider>,
    EnvironmentDependentProviderFactory {
    override fun isSupported(config: Config.Scope?): Boolean = Profile.isFeatureEnabled(Profile.Feature.ORGANIZATION)
}

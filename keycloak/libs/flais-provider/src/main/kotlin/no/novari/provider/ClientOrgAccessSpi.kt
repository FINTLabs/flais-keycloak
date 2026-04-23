package no.novari.provider

import org.keycloak.common.Profile
import org.keycloak.provider.Provider
import org.keycloak.provider.ProviderFactory
import org.keycloak.provider.Spi

class ClientOrgAccessSpi : Spi {
    override fun isInternal(): Boolean = true

    override fun getName(): String = "client-org-access"

    override fun getProviderClass(): Class<out Provider> = ClientOrgAccessProvider::class.java

    override fun getProviderFactoryClass(): Class<out ProviderFactory<*>> = ClientOrgAccessProviderFactory::class.java

    override fun isEnabled(): Boolean = Profile.isFeatureEnabled(Profile.Feature.ORGANIZATION)
}

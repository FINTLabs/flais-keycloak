package no.fintlabs.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a Keycloak realm export that is used in tests
 */
class KcConfig private constructor(
    private val realm: RealmExport,
) {
    @Serializable
    data class RealmExport(
        val realm: String,
        val organizations: List<Organization> = emptyList(),
        val clients: List<Client> = emptyList(),
        val identityProviders: List<IdentityProvider> = emptyList(),
    )

    @Serializable
    data class Organization(
        val id: String,
        val name: String,
        val alias: String,
        val enabled: Boolean = true,
        val description: String? = null,
        val attributes: Map<String, List<String>> = emptyMap(),
        val domains: List<Domain> = emptyList(),
        val identityProviders: List<OrgIdentityProviderRef> = emptyList(),
    )

    @Serializable
    data class Domain(
        val name: String,
        val verified: Boolean = false,
    )

    @Serializable
    data class OrgIdentityProviderRef(
        val alias: String,
        val enabled: Boolean = true,
        val config: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class IdentityProvider(
        val alias: String,
        val displayName: String? = null,
        val providerId: String,
        val enabled: Boolean = true,
        val linkOnly: Boolean = false,
        val config: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class Client(
        val clientId: String,
        val protocol: String,
        val enabled: Boolean = true,
        val publicClient: Boolean,
        val standardFlowEnabled: Boolean,
        val redirectUris: List<String> = emptyList(),
        val webOrigins: List<String> = emptyList(),
        val attributes: Map<String, String> = emptyMap(),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromString(raw: String): KcConfig = KcConfig(json.decodeFromString(RealmExport.serializer(), raw))

        fun fromFile(path: Path): KcConfig = fromString(Files.readString(path))
    }

    val realmName: String get() = realm.realm

    fun orgAliases(): Set<String> = realm.organizations.map { it.alias }.toSet()

    fun idpAliases(): Set<String> = realm.identityProviders.map { it.alias }.toSet()

    fun clientIds(): Set<String> = realm.clients.map { it.clientId }.toSet()

    fun idpAliasesForOrg(orgAlias: String): List<String> {
        val org = requireOrg(orgAlias)
        return org.identityProviders
            .sortedByDescending { it.enabled }
            .map { it.alias }
    }

    fun requireOrg(orgAlias: String): Organization =
        realm.organizations.find { it.alias == orgAlias }
            ?: error("Organization with alias '$orgAlias' not found. Known orgs: ${orgAliases().sorted()}")

    fun requireClient(clientId: String): Client =
        realm.clients.find { it.clientId == clientId }
            ?: error("Client '$clientId' not found. Known clients: ${clientIds().sorted()}")

    fun orgHasIdp(
        orgAlias: String,
        idpAlias: String,
    ): Boolean = requireOrg(orgAlias).identityProviders.any { it.alias == idpAlias }
}

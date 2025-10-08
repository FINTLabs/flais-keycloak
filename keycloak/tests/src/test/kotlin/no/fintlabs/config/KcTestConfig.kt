package no.fintlabs.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path


/**
 * Loads a Keycloak realm export (like the one you pasted) and exposes
 * convenience lookups for tests. Uses kotlinx.serialization.
 */
class KcTestConfig private constructor(
    private val realm: RealmExport
) {

    @Serializable
    data class RealmExport(
        val realm: String,
        val organizations: List<Organization> = emptyList(),
        val clients: List<Client> = emptyList(),
        val identityProviders: List<IdentityProvider> = emptyList()
    )

    @Serializable
    data class Organization(
        val id: String? = null,
        val name: String,
        val alias: String,
        val enabled: Boolean = true,
        val description: String? = null,
        val attributes: Map<String, String> = emptyMap(),
        val domains: List<Domain> = emptyList(),
        val identityProviders: List<OrgIdentityProviderRef> = emptyList()
    )

    @Serializable
    data class Domain(
        val name: String,
        val verified: Boolean = false
    )

    @Serializable
    data class OrgIdentityProviderRef(
        val alias: String,
        val enabled: Boolean = true,
        val config: Map<String, String> = emptyMap()
    )

    @Serializable
    data class IdentityProvider(
        val alias: String,
        val displayName: String? = null,
        val providerId: String,
        val enabled: Boolean = true,
        val linkOnly: Boolean = false,
        val config: Map<String, String> = emptyMap()
    )

    @Serializable
    data class Client(
        val clientId: String,
        val enabled: Boolean = true,
        val publicClient: Boolean? = null,
        val redirectUris: List<String> = emptyList(),
        val webOrigins: List<String> = emptyList(),
        val attributes: Map<String, String> = emptyMap()
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromString(raw: String): KcTestConfig =
            KcTestConfig(json.decodeFromString(RealmExport.serializer(), raw))

        fun fromResource(path: String): KcTestConfig =
            fromString(object {}.javaClass.getResourceAsStream(path)!!.readBytes().decodeToString())

        fun fromFile(path: Path): KcTestConfig =
            fromString(Files.readString(path))
    }

    val realmName: String get() = realm.realm

    /** All organization aliases, e.g. ["idporten", "innlandet", "telemark"] */
    fun orgAliases(): Set<String> = realm.organizations.map { it.alias }.toSet()

    /** All identity provider aliases declared at realm level */
    fun idpAliases(): Set<String> = realm.identityProviders.map { it.alias }.toSet()

    /** All clientIds */
    fun clientIds(): Set<String> = realm.clients.map { it.clientId }.toSet()

    /** IDP aliases that are linked to the given organization (enabled ones first). */
    fun idpAliasesForOrg(orgAlias: String): List<String> {
        val org = requireOrg(orgAlias)
        return org.identityProviders
            .sortedByDescending { it.enabled }
            .map { it.alias }
    }

    /** Get org, or throw useful assertion-friendly error */
    fun requireOrg(orgAlias: String): Organization =
        realm.organizations.find { it.alias == orgAlias }
            ?: error("Organization with alias '$orgAlias' not found. Known orgs: ${orgAliases().sorted()}")

    /** Get IDP, or throw useful assertion-friendly error */
    fun requireIdp(idpAlias: String): IdentityProvider =
        realm.identityProviders.find { it.alias == idpAlias }
            ?: error("Identity provider '$idpAlias' not found. Known IDPs: ${idpAliases().sorted()}")

    /** Get client, or throw useful assertion-friendly error */
    fun requireClient(clientId: String): Client =
        realm.clients.find { it.clientId == clientId }
            ?: error("Client '$clientId' not found. Known clients: ${clientIds().sorted()}")

    /** True if the org is allowed to use the given IDP (linked at org level). */
    fun orgHasIdp(orgAlias: String, idpAlias: String): Boolean =
        requireOrg(orgAlias).identityProviders.any { it.alias == idpAlias }

    /** Convenience for tests where an org has exactly one IDP and should redirect */
    fun singleIdpForOrg(orgAlias: String): String =
        idpAliasesForOrg(orgAlias).singleOrNull()
            ?: error("Expected a single IDP for '$orgAlias' but found ${idpAliasesForOrg(orgAlias)}")
}
package no.fintlabs.utils

import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.FederatedIdentityRepresentation
import org.keycloak.representations.idm.MemberRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.keycloak.util.JsonSerialization

/**
 * Utility object for interacting with a Keycloak instance using the Keycloak Admin Client.
 *
 * This object provides helper functions for connecting to a Keycloak server,
 * performing common administrative operations such as finding and deleting users,
 * and retrieving federated identity information.
 */
object KcAdminClient {
    private const val ADMIN_REALM = "master"
    private const val ADMIN_CLIENT_ID = "admin-cli"

    fun connect(
        env: KcComposeEnvironment,
        realm: String,
    ): Pair<Keycloak, RealmResource> {
        val kc =
            KeycloakBuilder
                .builder()
                .serverUrl(env.keycloakServiceUrl())
                .realm(ADMIN_REALM)
                .clientId(ADMIN_CLIENT_ID)
                .username(env.keycloakAdminUser)
                .password(env.keycloakAdminPassword)
                .build()
        return kc to kc.realm(realm)
    }

    fun addUserToOrg(
        realm: RealmResource,
        userId: String,
        orgId: String,
    ) {
        realm
            .organizations()
            .get(orgId)
            .members()
            .addMember(userId)
    }

    fun createUser(
        realm: RealmResource,
        username: String,
        email: String,
        firstName: String,
        lastName: String,
        enabled: Boolean = true,
        realmRoleNames: List<String> = emptyList(),
    ): String {
        val rep =
            UserRepresentation().apply {
                this.username = username
                this.email = email
                this.firstName = firstName
                this.lastName = lastName
                this.isEnabled = enabled
            }

        val resp = realm.users().create(rep)
        val userId =
            resp.use {
                if (it.status !in 200..299) {
                    throw IllegalStateException("Failed to create user: HTTP ${it.status}")
                }
                CreatedResponseUtil.getCreatedId(it)
            }

        if (realmRoleNames.isNotEmpty()) {
            val userResource = realm.users().get(userId)

            val roleReps =
                realmRoleNames.map { roleName ->
                    realm.roles().get(roleName).toRepresentation()
                }

            userResource
                .roles()
                .realmLevel()
                .add(roleReps)
        }

        return userId
    }

    fun findUserByUsername(
        realm: RealmResource,
        username: String,
    ): UserRepresentation? = realm.users().searchByUsername(username, true).firstOrNull()

    fun deleteUser(
        realm: RealmResource,
        userId: String,
    ) {
        realm.users().delete(userId)
    }

    fun deleteAllUsers(realm: RealmResource) {
        val users = realm.users().list()
        users.forEach { user ->
            realm.users().delete(user.id)
        }
    }

    fun getFederatedIdentities(
        realm: RealmResource,
        userId: String,
    ): List<FederatedIdentityRepresentation> =
        realm
            .users()
            .get(userId)
            .federatedIdentity
            .toList()

    fun getOrgMember(
        realm: RealmResource,
        orgId: String,
        userId: String,
    ): MemberRepresentation? =
        runCatching {
            realm
                .organizations()
                .get(orgId)
                .members()
                .member(userId)
                .toRepresentation()
        }.getOrNull()

    fun resetRealmFromJson(
        env: KcComposeEnvironment,
        kcJson: String,
    ) {
        val rep: RealmRepresentation = JsonSerialization.readValue(kcJson, RealmRepresentation::class.java)

        val (kc, _) = connect(env, ADMIN_REALM)
        kc.use { keycloak ->
            val realms = keycloak.realms()
            runCatching { realms.realm(rep.realm).remove() }
            realms.create(rep)
        }
    }

    fun patchIdpAuthorizationUrls(
        env: KcComposeEnvironment,
        realmName: String,
        newBaseUrl: String,
    ) {
        val (kc, realm) = connect(env, realmName)
        kc.use {
            realm
                .identityProviders()
                .findAll()
                .asSequence()
                .map { it.alias }
                .forEach { alias ->
                    val res = realm.identityProviders().get(alias)
                    val rep = res.toRepresentation()

                    val cfg = (rep.config ?: emptyMap()).toMutableMap()
                    val oldAuth = cfg["authorizationUrl"] ?: return@forEach

                    val idx = oldAuth.indexOf("/application")
                    val newAuth = newBaseUrl.trimEnd('/') + oldAuth.substring(idx)

                    cfg["authorizationUrl"] = newAuth
                    rep.config = cfg
                    res.update(rep)
                }
        }
    }

    fun setClientProtocolMapperConfig(
        realm: RealmResource,
        clientId: String,
        mapperId: String,
        configKey: String,
        configValue: String,
    ) {
        val clientUuid =
            realm
                .clients()
                .findByClientId(clientId)
                .firstOrNull()
                ?.id
                ?: throw IllegalArgumentException("Client not found: clientId=$clientId")

        val client = realm.clients().get(clientUuid)
        val mappers = client.protocolMappers

        val mapper: ProtocolMapperRepresentation =
            mappers.mappers
                .firstOrNull { it.id == mapperId }
                ?: throw IllegalArgumentException("Protocol mapper not found: name=$mapperId (clientId=$clientId)")

        val newConfig = (mapper.config ?: emptyMap()).toMutableMap()
        newConfig[configKey] = configValue
        mapper.config = newConfig

        mappers.update(mapper.id, mapper)
    }
}

package no.fintlabs.utils

import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.FederatedIdentityRepresentation
import org.keycloak.representations.idm.MemberRepresentation
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

        return userId
    }

    fun findUserByEmail(
        realm: RealmResource,
        email: String,
    ): UserRepresentation? = realm.users().searchByEmail(email, true).firstOrNull()

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
}

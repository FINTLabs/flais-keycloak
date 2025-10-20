package no.fintlabs.utils

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.FederatedIdentityRepresentation
import org.keycloak.representations.idm.UserRepresentation

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

    fun findUserByEmail(
        realm: RealmResource,
        email: String,
    ): UserRepresentation? = realm.users().searchByEmail(email, true).firstOrNull()

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
}

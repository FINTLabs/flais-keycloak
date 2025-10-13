package no.fintlabs.utils

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.FederatedIdentityRepresentation
import org.keycloak.representations.idm.UserRepresentation

object KcAdminClient {
    private const val adminRealm = "master"
    private const val adminClientId = "admin-cli"

    fun connect(env: KcComposeEnvironment, realm: String): Pair<Keycloak, RealmResource> {
        val kc = KeycloakBuilder.builder()
            .serverUrl(env.keycloakServiceUrl())
            .realm(adminRealm)
            .clientId(adminClientId)
            .username(env.keycloakAdminUser)
            .password(env.keycloakAdminPassword)
            .build()
        return kc to kc.realm(realm)
    }

    fun findUserByEmail(realm: RealmResource, email: String): UserRepresentation? =
        realm.users().searchByEmail(email, true).firstOrNull()

    fun deleteUser(realm: RealmResource, userId: String) {
        realm.users().delete(userId)
    }

    fun getFederatedIdentities(realm: RealmResource, userId: String): List<FederatedIdentityRepresentation> =
        realm.users().get(userId).federatedIdentity.toList()
}

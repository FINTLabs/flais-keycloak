package no.novari.test.common.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.FederatedIdentityRepresentation
import org.keycloak.representations.idm.MemberRepresentation
import org.keycloak.representations.idm.PartialImportRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.keycloak.util.JsonSerialization
import java.util.UUID

/**
 * Utility wrapper around the Keycloak Admin Client used in tests.
 * Provides convenience functions for connecting to a realm, managing users and organization membership,
 * and performing on-demand tasks
 */
object KcAdminClient {
    private const val ADMIN_REALM = "master"
    private const val ADMIN_CLIENT_ID = "admin-cli"

    fun connect(
        env: KcEnvironment,
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
        env: KcEnvironment,
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
        env: KcEnvironment,
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

    fun createScaleTestUser(
        username: String,
        externalId: String,
        enabled: Boolean = true,
        email: String = username,
        roles: List<String> = listOf("reader"),
    ): UserRepresentation =
        UserRepresentation().apply {
            id = UUID.randomUUID().toString()
            this.username = username
            this.email = email
            isEnabled = enabled
            isEmailVerified = true
            realmRoles = listOf("scim-managed")
            attributes =
                mapOf(
                    "externalId" to listOf(externalId),
                    "roles" to roles,
                    "rawRoles" to
                        roles.map { role ->
                            buildJsonObject {
                                put("value", role)
                                put("display", role)
                            }.toString()
                        },
                )
        }

    fun addScaleTestUsers(
        env: KcEnvironment,
        kcConfig: KcConfig,
        realmName: String,
        orgAlias: String,
        userCount: Int = 10_000,
        importBatchSize: Int = 500,
        membershipBatchSize: Int = 50,
        failureThresholdPercent: Double = 5.0,
    ): List<UserRepresentation> {
        require(userCount > 0) { "userCount must be positive" }
        require(importBatchSize > 0) { "importBatchSize must be positive" }
        require(membershipBatchSize > 0) { "membershipBatchSize must be positive" }
        require(failureThresholdPercent in 0.0..100.0) { "failureThresholdPercent must be between 0 and 100" }
        val organizationId = kcConfig.requireOrg(orgAlias).id
        val users =
            List(userCount) { index ->
                createScaleTestUser(
                    username = "benchmark-${index.toString().padStart(7, '0')}@$orgAlias.no",
                    externalId = "benchmark-$index",
                    enabled = index % 2 == 0,
                )
            }
        val imported = mutableListOf<UserRepresentation>()

        fun checkFailures(
            name: String,
            failures: Int,
        ) {
            val percentage = failures * 100.0 / userCount
            check(percentage <= failureThresholdPercent) {
                "$name failed for $failures/$userCount users ($percentage%)"
            }
        }

        try {
            users.chunked(importBatchSize).forEach { batch ->
                val (admin, realm) = connect(env, realmName)
                admin.use {
                    runCatching {
                        val request =
                            PartialImportRepresentation().apply {
                                this.users = batch
                                ifResourceExists = PartialImportRepresentation.Policy.FAIL.name
                            }
                        realm.partialImport(request).use { response ->
                            val body = response.readEntity(String::class.java)
                            check(response.status == 200) { "User import failed: HTTP ${response.status}: $body" }
                            val result = Json.parseToJsonElement(body).jsonObject
                            check(result.getValue("added").jsonPrimitive.int == batch.size) { "Incomplete import: $body" }
                        }
                    }.onSuccess {
                        imported.addAll(batch)
                    }.onFailure {
                        System.err.println("Failed importing ${batch.first().username} to ${batch.last().username}: ${it.message}")
                    }
                }
            }
            checkFailures("User import", userCount - imported.size)

            val failedMemberships =
                imported.chunked(membershipBatchSize).sumOf { batch ->
                    val (admin, realm) = connect(env, realmName)
                    admin.use {
                        batch.count { user ->
                            runCatching {
                                realm.organizations().get(organizationId).members().addMember(user.id).use { response ->
                                    check(response.status in 200..299) { "HTTP ${response.status}" }
                                }
                            }.onFailure {
                                System.err.println("Failed adding ${user.username} to organization: ${it.message}")
                            }.isFailure
                        }
                    }
                }
            checkFailures("Organization membership", failedMemberships)
            println("Imported ${imported.size}/$userCount test users; $failedMemberships membership failures")
            return users
        } catch (failure: Exception) {
            // The caller cannot clean up until this method has returned its users.
            runCatching {
                val (admin, realm) = connect(env, realmName)
                admin.use { deleteUsers(realm, users) }
            }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
    }

    /** Attempts every deletion without hiding a failure from the test itself. */
    fun deleteUsers(
        realm: RealmResource,
        users: List<UserRepresentation>,
    ) {
        users.forEach { user ->
            runCatching {
                realm.users().delete(user.id).use { response ->
                    check(response.status in 200..299 || response.status == 404) { "HTTP ${response.status}" }
                }
            }.onFailure { System.err.println("Could not delete test user ${user.id}: ${it.message}") }
        }
    }
}

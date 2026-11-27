package no.novari.test.integration.application.flows

import no.novari.authenticator.broker.DetectExistingUserAuthenticatorConfig.REQUIRED_ROLE
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvironmentExtension::class)
class FlaisFirstBrokerLoginScimFlowTest {
    private val clientId = Clients.FLAIS_KEYCLOAK_DEMO

    @ParameterizedTest(name = "first login without scim managed role for org ({0}) returns error page")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `first login with existing unlinked user without scim managed role returns error page`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val idpAlias = Idps.entra(orgAlias)
        val user =
            ScimFlow
                .provision(
                    env = env,
                    kcConfig = kcConfig,
                    orgAlias = orgAlias,
                    users = listOf(ScimFlow.randomUser(orgAlias)),
                ).single()
        val (kc, realmRes) = KcAdminClient.connect(env, Realms.EXTERNAL)

        kc.use {
            val provisionedUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
            assertNotNull(provisionedUser)
            val userId = provisionedUser.id
            val userResource = realmRes.users().get(userId)
            val links = KcAdminClient.getFederatedIdentities(realmRes, userId)
            val existingLink = links.singleOrNull { it.identityProvider == idpAlias }
            if (existingLink != null) {
                userResource.removeFederatedIdentity(idpAlias)
            }

            val configId =
                realmRes
                    .flows()
                    .getExecutions("flais-first-broker-login-scim")
                    .single { it.providerId == "detect-existing-user-authenticator" }
                    .authenticationConfig
            val requiredRoleName =
                realmRes
                    .flows()
                    .getAuthenticatorConfig(configId)
                    .config
                    .getValue(REQUIRED_ROLE)
            val scimRole = realmRes.roles().get(requiredRoleName).toRepresentation()
            userResource.roles().realmLevel().remove(listOf(scimRole))

            assertFalse(
                userResource
                    .roles()
                    .realmLevel()
                    .listEffective()
                    .any { it.name == requiredRoleName },
            )
            assertFalse(KcAdminClient.getFederatedIdentities(realmRes, userId).any { it.identityProvider == idpAlias })
            loginWithMicrosoftScimUser(
                env = env,
                clientId = clientId,
                orgAlias = orgAlias,
                idpAlias = idpAlias,
                user = user,
                hasIdpSelector = orgAlias == Orgs.TELEMARK,
            ).use { resp ->
                assertEquals(403, resp.code)
                val context = KcContextParser.parseKcContext(resp.body.string())
                assertEquals(Pages.ERROR, context.pageId)
                assertEquals("error", context.message!!.type)
                assertEquals(
                    "Your account has not yet been provisioned. It may take up to 1 hour for your account to become available after you have been added. Please contact your administrator.",
                    context.message.summary,
                )
            }

            assertFalse(KcAdminClient.getFederatedIdentities(realmRes, userId).any { it.identityProvider == idpAlias })
        }
    }

    @ParameterizedTest(name = "first login without existing user for org ({0}) is rejected and does not create user")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `first login without existing user is rejected and does not create user`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val idpAlias = Idps.entra(orgAlias)
        val users =
            listOf(Orgs.TELEMARK, Orgs.ROGALAND).associateWith { orgAlias ->
                List(2) { ScimFlow.randomUser(orgAlias) }
            }

        val (kc, realmRes) = KcAdminClient.connect(env, Realms.EXTERNAL)

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                loginWithMicrosoftScimUser(
                    env,
                    clientId,
                    orgAlias,
                    idpAlias,
                    user,
                    hasIdpSelector = orgAlias == Orgs.TELEMARK,
                ).use { resp ->
                    assertEquals(403, resp.code)

                    val html = resp.body.string()
                    val kc = KcContextParser.parseKcContext(html)

                    assertEquals(Pages.ERROR, kc.pageId)
                    assertEquals("error", kc.message!!.type)
                    assertEquals(
                        "Your account has not yet been provisioned. It may take up to 1 hour for your account to become available after you have been added. Please contact your administrator.",
                        kc.message.summary,
                    )
                }
                assertNull(KcAdminClient.findUserByUsername(realmRes, user.userName))
            }
        }
    }

    @ParameterizedTest(
        name = "first login with existing unlinked user for org ({0}) links existing user",
    )
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `first login with existing unlinked user continues and links existing user`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
        scimUsers: List<ScimUser>,
    ) {
        val idpAlias = Idps.entra(orgAlias)
        val orgId = kcConfig.requireOrg(orgAlias).id
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@$orgAlias.no")
                }
            }

        val (kc, realmRes) = KcAdminClient.connect(env, Realms.EXTERNAL)

        kc.use {
            val provisionedUser =
                KcAdminClient.findUserByUsername(realmRes, user.userName)
            assertNotNull(provisionedUser)

            val userId = provisionedUser.id
            val userResource = realmRes.users().get(userId)

            val links = KcAdminClient.getFederatedIdentities(realmRes, userId)
            if (links.any { it.identityProvider == idpAlias }) {
                userResource.removeFederatedIdentity(idpAlias)
            }

            val unlinkedIdentities =
                KcAdminClient.getFederatedIdentities(realmRes, userId)
            assertFalse(unlinkedIdentities.any { it.identityProvider == idpAlias })

            loginWithMicrosoftScimUser(
                env = env,
                clientId = clientId,
                orgAlias = orgAlias,
                idpAlias = idpAlias,
                user = user,
                hasIdpSelector = orgAlias == Orgs.TELEMARK,
            ).use { resp ->
                assertEquals(200, resp.code)

                val existingUser =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(existingUser)
                assertEquals(userId, existingUser.id)

                val updatedLinks =
                    KcAdminClient.getFederatedIdentities(realmRes, userId)
                assertEquals(
                    1,
                    updatedLinks.count { it.identityProvider == idpAlias },
                )

                val member =
                    KcAdminClient.getOrgMember(realmRes, orgId, userId)
                assertNotNull(member)
            }
        }
    }
}

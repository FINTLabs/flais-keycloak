package no.novari.test.integration.application.scim

import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.common.utils.ScimHttpClient
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.images.builder.Transferable
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvironmentExtension::class)
class ComplianceTest {
    private val realm = Realms.EXTERNAL

    private val users =
        mapOf(
            Orgs.TELEMARK to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = Users.JON_TELEMARK,
                        userName = Users.JON_TELEMARK,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.JON_TELEMARK_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.JON_FIRST_NAME,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.JON_TELEMARK_EMAIL,
                            ),
                    ),
                ),
            Orgs.ROGALAND to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = Users.JON_ROGALAND,
                        userName = Users.JON_ROGALAND,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.JON_ROGALAND_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.JON_FIRST_NAME,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.JON_ROGALAND_EMAIL,
                            ),
                    ),
                ),
        )

    @ParameterizedTest(name = "flais-scim-server for org ({0}) passes compliance tests")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `flais-scim-server for org passes compliance tests`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val token = ScimHttpClient.getAccessToken("${env.flaisScimAuthUrl()}/token")
        val container = createScimverifyContainer(env, kcConfig, orgAlias, token)

        users[orgAlias]?.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    assertEquals(201, resp.code)
                }
        }

        assertContainerOutput(container)
    }

    private fun assertContainerOutput(container: GenericContainer<*>) {
        container.start()

        await().withPollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(60)).until {
            val state = container.currentContainerInfo?.state
            state != null && !state.running!!
        }

        val logs = container.logs

        assertTrue(logs.isNotEmpty())
        assertTrue(logs.contains("All tests passed successfully"))
        assertFalse(logs.contains("failing tests"))
    }

    private fun createScimverifyContainer(
        env: KcEnvironment,
        kcConfig: KcConfig,
        orgAlias: String,
        token: String,
    ): GenericContainer<*> {
        val image =
            ImageFromDockerfile("scimverify", false)
                .withFileFromPath(".", Paths.get("tools/scimverify").toAbsolutePath().normalize())
        val svConfig =
            Files.readString(Paths.get("config/scimverify/entra-$orgAlias.yaml").toAbsolutePath().normalize())

        return GenericContainer(image)
            .withCopyToContainer(
                Transferable.of(svConfig),
                "/app/config.yaml",
            ).withEnv(
                "BASE_URL",
                "${env.keycloakInternalUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}/",
            ).withEnv("CONFIG", "./config.yaml")
            .withEnv("BEARER_TOKEN", token)
            .withNetworkMode("keycloak")
    }
}

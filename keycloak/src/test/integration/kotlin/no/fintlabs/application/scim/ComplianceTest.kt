package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.ScimFlow.getAccessToken
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.images.builder.Transferable
import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvExtension::class)
class ComplianceTest {
    private val realm = "external"

    @Test
    fun `flais-scim-server for org (Telemark) passes compliance tests`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = "telemark"
        val token = getAccessToken(env.scimClientTelemarkUrl())
        val container = createScimverifyContainer(env, kcConfig, orgAlias, token)
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val email = "scimverify.user@telemark.no"
            val username = "scimverify.user@telemark.no"
            val firstname = "Scimverify"
            val lastname = "User"

            val userId =
                KcAdminClient.createUser(
                    realmRes,
                    username,
                    email,
                    firstname,
                    lastname,
                    realmRoleNames = listOf("scim-managed"),
                )
            assertNotNull(userId)

            KcAdminClient.addUserToOrg(realmRes, userId, kcConfig.requireOrg(orgAlias).id)

            assertContainerOutput(container)
        }
    }

    @Test
    fun `flais-scim-server for org (Rogaland) passes compliance tests`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = "rogaland"
        val token = getAccessToken(env.scimClientRogalandUrl())
        val container = createScimverifyContainer(env, kcConfig, orgAlias, token)
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val email = "scimverify.user@rogaland.no"
            val username = "scimverify.user@rogaland.no"
            val firstname = "Scimverify"
            val lastname = "User"

            val userId =
                KcAdminClient.createUser(
                    realmRes,
                    username,
                    email,
                    firstname,
                    lastname,
                    realmRoleNames = listOf("scim-managed"),
                )
            assertNotNull(userId)

            KcAdminClient.addUserToOrg(realmRes, userId, kcConfig.requireOrg(orgAlias).id)

            assertContainerOutput(container)
        }
    }

    private fun assertContainerOutput(container: GenericContainer<*>) {
        container.start()

        // We need to wait for scimverify to finish before asserting
        while (true) {
            val state = container.currentContainerInfo?.state
            if (state != null && !state.running!!) break
            Thread.sleep(200)
        }

        val logs = container.logs

        assertTrue(logs.isNotEmpty())
        assertTrue(logs.contains("All tests passed successfully"))
    }

    private fun createScimverifyContainer(
        env: KcComposeEnvironment,
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

package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.ScimFlow
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class ProvisionTest {
    private val usersTelemark =
        listOf(
            ScimFlow.ScimUser(
                externalId = "11111111-1111-1111-1111-111111111111",
                userName = "alice.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@telemark.no", primary = true)),
            ),
            ScimFlow.ScimUser(
                externalId = "22222222-2222-2222-2222-222222222222",
                userName = "jon.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@telemark.no", primary = true)),
            ),
        )

    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "11111111-1111-1111-1111-111111111111",
                userName = "alice.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@rogaland.no", primary = true)),
            ),
            ScimFlow.ScimUser(
                externalId = "22222222-2222-2222-2222-222222222222",
                userName = "jon.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@rogaland.no", primary = true)),
            ),
        )

    @Test
    fun `provision users to org (telemark) returns ok`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        usersTelemark.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("telemark").id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    Assertions.assertEquals(201, resp.code)
                }
        }
    }

    @Test
    fun `provision users to org (rogaland) returns ok`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        usersRogaland.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("rogaland").id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    Assertions.assertEquals(201, resp.code)
                }
        }
    }
}

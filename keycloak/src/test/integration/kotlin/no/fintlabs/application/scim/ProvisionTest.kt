package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.ScimFlow
import no.fintlabs.utils.ScimFlow.provisionUsers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class ProvisionTest {
    private val usersTelemark =
        listOf(
            ScimFlow.ScimUser(
                externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                userName = "alice.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@telemark.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "CiQyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjISBWxvY2Fs",
                userName = "jon.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@telemark.no")),
                groups = emptyList(),
            ),
        )

    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                userName = "alice.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@rogaland.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "CiQyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjISEWxvY2Fs",
                userName = "jon.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@rogaland.no")),
                groups = emptyList(),
            ),
        )

    @Test
    fun `provision users to org (telemark) returns ok`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        provisionUsers(
            env.scimClientTelemarkUrl(),
            kcConfig.requireOrg("telemark").id,
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
    }

    @Test
    fun `provision users to org (rogaland) returns ok`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        provisionUsers(
            env.scimClientRogalandUrl(),
            kcConfig.requireOrg("rogaland").id,
            usersRogaland,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
    }
}

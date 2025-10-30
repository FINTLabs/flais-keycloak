package no.fintlabs.application.scim

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.ScimFlow
import no.fintlabs.utils.ScimFlow.provisionUsers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class ProvisionTest {
    val usersTelemark =
        listOf(
            ScimFlow.ScimUser(
                externalId = "93b2d741-c6b2-48e2-aea9-945885596958",
                userName = "alice@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Anders"),
                emails = listOf(ScimFlow.ScimUser.Email("alice@telemark.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "f4bfe76a-0b8f-4a49-a81a-5b07244c8f95",
                userName = "bob@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Bob", "Berg"),
                emails = listOf(ScimFlow.ScimUser.Email("bob@telemark.no")),
                groups = emptyList(),
            ),
        )
    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "93b2d741-c6b2-48e2-aea9-945885596958",
                userName = "alice@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Anders"),
                emails = listOf(ScimFlow.ScimUser.Email("alice@rogaland.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "f4bfe76a-0b8f-4a49-a81a-5b07244c8f95",
                userName = "bob@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Bob", "Berg"),
                emails = listOf(ScimFlow.ScimUser.Email("bob@rogaland.no")),
                groups = emptyList(),
            ),
        )

    @Test
    @Order(1)
    fun `provision users to org (telemark) returns ok`(env: KcComposeEnvironment) {
        provisionUsers(
            "${env.scimClientTelemarkUrl()}/provision/d46586ca-9cbb-46c8-924c-061aaef9925e".toHttpUrl(),
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
    }

    @Test
    @Order(2)
    fun `provision users to org (rogaland) returns ok`(env: KcComposeEnvironment) {
        provisionUsers(
            "${env.scimClientRogalandUrl()}/provision/1985b29e-f1b0-43c7-8e4e-ef61bccfbefc".toHttpUrl(),
            usersRogaland,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
    }
}

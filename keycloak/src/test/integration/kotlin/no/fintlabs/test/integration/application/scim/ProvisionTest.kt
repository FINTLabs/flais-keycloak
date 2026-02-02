package no.fintlabs.test.integration.application.scim

import no.fintlabs.test.common.config.KcConfig
import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.integration.utils.ScimFlow
import no.fintlabs.test.integration.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvExtension::class)
class ProvisionTest {
    private val users =
        mapOf(
            "telemark" to
                listOf(
                    ScimUser(
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@telemark.no",
                        active = true,
                        name = ScimUser.Name("Alice", "Basic"),
                        emails = listOf(ScimUser.Email("alice.basic@telemark.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                    ),
                    ScimUser(
                        externalId = "22222222-2222-2222-2222-222222222222",
                        userName = "jon.basic@telemark.no",
                        active = true,
                        name = ScimUser.Name("Jon", "Basic"),
                        emails = listOf(ScimUser.Email("jon.basic@telemark.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                    ),
                ),
            "rogaland" to
                listOf(
                    ScimUser(
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@rogaland.no",
                        active = true,
                        name = ScimUser.Name("Alice", "Basic"),
                        emails = listOf(ScimUser.Email("alice.basic@rogaland.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                    ),
                    ScimUser(
                        externalId = "22222222-2222-2222-2222-222222222222",
                        userName = "jon.basic@rogaland.no",
                        active = true,
                        name = ScimUser.Name("Jon", "Basic"),
                        emails = listOf(ScimUser.Email("jon.basic@rogaland.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                    ),
                ),
        )

    @ParameterizedTest(name = "provision users to org ({0}) returns ok")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `provision users to org returns ok`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
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
    }
}

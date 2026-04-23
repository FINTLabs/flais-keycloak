package no.novari.test.integration.application.scim

import no.novari.test.common.config.KcConfig
import no.novari.test.common.extensions.kc.KcEnvExtension
import no.novari.test.common.utils.kc.KcEnvironment
import no.novari.test.integration.utils.ScimFlow
import no.novari.test.integration.utils.ScimFlow.ScimUser
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
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
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
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "alice.basic@telemark.no"),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
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
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "jon.basic@telemark.no"),
                    ),
                ),
            "rogaland" to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
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
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "alice.basic@rogaland.no"),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
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
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "jon.basic@rogaland.no"),
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

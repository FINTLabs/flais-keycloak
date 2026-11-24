package no.novari.test.integration.application.authenticators

import no.novari.authenticator.org.OrgRememberUiAuthenticatorConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.continueFromIdpSelector
import no.novari.test.integration.utils.KcFlow.continueFromOrgRemember
import no.novari.test.integration.utils.KcFlow.continueFromOrgSelector
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import no.novari.utils.OrgRememberCookie.cookieName
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.TimeUnit

@ExtendWith(KcEnvironmentExtension::class)
class OrgRememberUiAuthenticatorTest {
    @ParameterizedTest
    @MethodSource("confirmationCases")
    fun `missing remembered cookie shows confirmation`(
        clientId: String,
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()

        openRememberStep(env, client, orgAlias, clientId).use { response ->
            assertEquals(Pages.FLAIS_ORG_REMEMBER, pageContext(response)?.pageId)
        }
    }

    @ParameterizedTest
    @MethodSource("organizationChoices")
    fun `explicit choice redirects to selected identity provider`(
        orgAlias: String,
        choice: Boolean,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        val confirmation = openConfirmation(env, client, orgAlias)

        continueFromOrgRemember(confirmation.url.loginAction!!, choice, client).use { response ->
            assertEquals(303, response.code)
            assertTrue(response.header("Location").orEmpty().contains("/realms/${Realms.EXTERNAL}/broker/${idpAlias(orgAlias)}/"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND, Orgs.IDPORTEN])
    fun `yes stores selected organization in the client cookie`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        val confirmation = openConfirmation(env, client, orgAlias)

        continueFromOrgRemember(confirmation.url.loginAction!!, true, client).use { response ->
            assertEquals(orgAlias, rememberedCookie(response).value)
        }
    }

    @ParameterizedTest
    @MethodSource("organizationChoices")
    fun `remember cookie has realm path security attributes and lifetime for the choice`(
        orgAlias: String,
        choice: Boolean,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        val confirmation = openConfirmation(env, client, orgAlias)

        continueFromOrgRemember(confirmation.url.loginAction!!, choice, client).use { response ->
            val cookie = rememberedCookie(response)
            assertEquals("/realms/${Realms.EXTERNAL}", cookie.path)
            assertTrue(cookie.httpOnly)
            assertTrue(cookie.persistent)
            val header = response.headers.values("Set-Cookie").single { it.startsWith("${cookieName(Clients.FLAIS_KEYCLOAK_DEMO)}=") }
            assertTrue(header.contains("SameSite=Lax", ignoreCase = true))
            val maxAge =
                if (choice) {
                    TimeUnit.DAYS.toSeconds(OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS.toLong())
                } else {
                    0L
                }
            assertTrue(header.contains("Max-Age=$maxAge", ignoreCase = true))
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["on", "yes", "no", "0", "1", "invalid"])
    fun `missing or invalid choice shows error page`(
        choice: String?,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        val confirmation = openConfirmation(env, client)

        submitInvalidChoice(confirmation, choice, client).use { response ->
            val kc = pageContext(response)
            assertEquals(Pages.ERROR, kc?.pageId)
            assertEquals("error", kc?.message?.type)
            assertEquals("Choose whether to remember your organization to continue", kc?.message?.summary)
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["on", "yes", "no", "0", "1", "invalid"])
    fun `missing or invalid choice leaves remembered organization unchanged`(
        choice: String?,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, Orgs.ROGALAND)
        val confirmation = openConfirmation(env, client, Orgs.TELEMARK)

        submitInvalidChoice(confirmation, choice, client).close()

        assertDefaultOrganization(env, client, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.ROGALAND)
    }

    @ParameterizedTest
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND, Orgs.IDPORTEN])
    fun `remembered organization is preselected on next login`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, orgAlias)

        assertDefaultOrganization(env, client, Clients.FLAIS_KEYCLOAK_DEMO, orgAlias)
    }

    @ParameterizedTest
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND, Orgs.IDPORTEN])
    fun `selecting remembered organization bypasses confirmation`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, orgAlias)

        openRememberStep(env, client, orgAlias).use { response ->
            assertEquals(303, response.code)
            assertTrue(response.header("Location").orEmpty().contains("/realms/${Realms.EXTERNAL}/broker/${idpAlias(orgAlias)}/"))
        }
    }

    @ParameterizedTest
    @MethodSource("organizationPairs")
    fun `selecting a different organization shows confirmation`(
        previousOrg: String,
        nextOrg: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, previousOrg)

        openRememberStep(env, client, nextOrg).use { response ->
            assertEquals(Pages.FLAIS_ORG_REMEMBER, pageContext(response)?.pageId)
        }
    }

    @ParameterizedTest
    @MethodSource("organizationPairs")
    fun `yes replaces the previously remembered organization`(
        previousOrg: String,
        nextOrg: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, previousOrg)
        val confirmation = openConfirmation(env, client, nextOrg)

        continueFromOrgRemember(confirmation.url.loginAction!!, true, client).use { response ->
            assertEquals(nextOrg, rememberedCookie(response).value)
        }
    }

    @ParameterizedTest
    @MethodSource("organizationPairs")
    fun `no expires the previously remembered cookie`(
        previousOrg: String,
        nextOrg: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, previousOrg)
        val confirmation = openConfirmation(env, client, nextOrg)

        continueFromOrgRemember(confirmation.url.loginAction!!, false, client).use { response ->
            val cookie = rememberedCookie(response)
            assertEquals("", cookie.value)
            assertTrue(cookie.expiresAt <= System.currentTimeMillis())
        }
    }

    @ParameterizedTest
    @MethodSource("organizationPairs")
    fun `no leaves no organization preselected on next login`(
        previousOrg: String,
        nextOrg: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, previousOrg)
        val confirmation = openConfirmation(env, client, nextOrg)
        continueFromOrgRemember(confirmation.url.loginAction!!, false, client).close()

        assertDefaultOrganization(env, client, Clients.FLAIS_KEYCLOAK_DEMO, "")
    }

    @ParameterizedTest
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `remembered organization is not preselected for another client`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        rememberOrganization(env, client, orgAlias)

        assertDefaultOrganization(env, client, Clients.FLAIS_KEYCLOAK_DEMO_ENTRA, "")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `changing remember preference in another client preserves the first client preference`(
        secondChoice: Boolean,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create()
        val firstClient = Clients.FLAIS_KEYCLOAK_DEMO
        val secondClient = Clients.FLAIS_KEYCLOAK_DEMO_ENTRA
        rememberOrganization(env, client, Orgs.TELEMARK, firstClient)
        rememberOrganization(env, client, Orgs.ROGALAND, secondClient)
        val confirmation = openConfirmation(env, client, Orgs.TELEMARK, secondClient)

        continueFromOrgRemember(confirmation.url.loginAction!!, secondChoice, client).use { response ->
            assertTrue(response.headers.values("Set-Cookie").none { it.startsWith("${cookieName(firstClient)}=") })
        }
        assertDefaultOrganization(env, client, firstClient, Orgs.TELEMARK)
    }

    private fun idpAlias(orgAlias: String): String = if (orgAlias == Orgs.IDPORTEN) Idps.IDPORTEN else Idps.entra(orgAlias)

    private fun assertDefaultOrganization(
        env: KcEnvironment,
        client: OkHttpClient,
        clientId: String,
        expectedOrg: String,
    ) {
        openAuthUrl(env = env, clientId = clientId, httpClient = client).use { response ->
            assertEquals(expectedOrg, pageContext(response)?.defaultSelectedOrg)
        }
    }

    private fun submitInvalidChoice(
        confirmation: KcContextParser.KcContext,
        choice: String?,
        client: OkHttpClient,
    ): Response {
        val body = FormBody.Builder()
        if (choice != null) body.add("remember_org", choice)
        val request =
            Request
                .Builder()
                .url(confirmation.url.loginAction!!)
                .post(body.build())
                .build()
        return client.newCall(request).execute()
    }

    private fun rememberOrganization(
        env: KcEnvironment,
        client: OkHttpClient,
        orgAlias: String,
        clientId: String = Clients.FLAIS_KEYCLOAK_DEMO,
    ) {
        val confirmation = openConfirmation(env, client, orgAlias, clientId)
        continueFromOrgRemember(confirmation.url.loginAction!!, true, client).close()
    }

    private fun openConfirmation(
        env: KcEnvironment,
        client: OkHttpClient,
        orgAlias: String = Orgs.TELEMARK,
        clientId: String = Clients.FLAIS_KEYCLOAK_DEMO,
    ): KcContextParser.KcContext =
        openRememberStep(env, client, orgAlias, clientId).use { response ->
            requireNotNull(pageContext(response)) { "Expected a login page, got HTTP ${response.code}" }
        }

    private fun openRememberStep(
        env: KcEnvironment,
        client: OkHttpClient,
        orgAlias: String,
        clientId: String = Clients.FLAIS_KEYCLOAK_DEMO,
    ): Response {
        var response = openAuthUrl(env = env, clientId = clientId, httpClient = client)
        var kc = pageContext(response)
        if (kc?.pageId == Pages.FLAIS_ORG_SELECTOR) {
            response.close()
            response = continueFromOrgSelector(kc.url.loginAction!!, orgAlias, client, hasRememberOrg = false)
            kc = pageContext(response)
        }
        if (kc?.pageId == Pages.FLAIS_ORG_IDP_SELECTOR) {
            response.close()
            response = continueFromIdpSelector(kc.url.loginAction!!, idpAlias(orgAlias), client, hasRememberOrg = false)
        }
        return response
    }

    private fun pageContext(response: Response): KcContextParser.KcContext? =
        if (response.code == 200) KcContextParser.parseKcContext(response.peekBody(Long.MAX_VALUE).string()) else null

    private fun rememberedCookie(response: Response): Cookie =
        Cookie.parseAll(response.request.url, response.headers).single {
            it.name == cookieName(Clients.FLAIS_KEYCLOAK_DEMO)
        }

    private companion object {
        val ORGANIZATIONS = listOf(Orgs.TELEMARK, Orgs.ROGALAND, Orgs.IDPORTEN)

        @JvmStatic
        fun confirmationCases(): List<Arguments> =
            ORGANIZATIONS.map { Arguments.of(Clients.FLAIS_KEYCLOAK_DEMO, it) } +
                listOf(
                    Arguments.of(Clients.FLAIS_KEYCLOAK_DEMO_TELEMARK, Orgs.TELEMARK),
                    Arguments.of(Clients.FLAIS_KEYCLOAK_DEMO_IDPORTEN, Orgs.IDPORTEN),
                )

        @JvmStatic
        fun organizationChoices(): List<Arguments> =
            ORGANIZATIONS.flatMap { org ->
                listOf(true, false).map { choice -> Arguments.of(org, choice) }
            }

        @JvmStatic
        fun organizationPairs(): List<Arguments> =
            ORGANIZATIONS.flatMap { previousOrg ->
                ORGANIZATIONS.filter { it != previousOrg }.map { nextOrg -> Arguments.of(previousOrg, nextOrg) }
            }
    }
}

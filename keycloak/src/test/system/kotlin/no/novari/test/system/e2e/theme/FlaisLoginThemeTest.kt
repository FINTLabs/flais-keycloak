package no.novari.test.system.e2e.theme

import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import no.novari.test.common.annotations.PwTest
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.pw.PwEnvironment
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Uris
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.system.utils.PwAutoLogin
import no.novari.test.system.utils.PwFlow
import no.novari.utils.OrgRememberCookie.cookieName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestTemplate
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.representations.idm.ClientRepresentation
import java.util.UUID
import java.util.regex.Pattern
import kotlin.collections.any
import kotlin.collections.orEmpty

@PwTest
class FlaisLoginThemeTest {
    @TestTemplate
    fun `login works with org selector`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
        scimUsers: List<ScimUser>,
    ) {
        val page = session.page
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@${Orgs.ROGALAND}.no")
                }
            }

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.continueFromOrgSelector(page, Orgs.ROGALAND_DISPLAY_NAME)
        PwFlow.submit(page)

        PwAutoLogin.login(page, user)

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login works with idp selector`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
        scimUsers: List<ScimUser>,
    ) {
        val page = session.page
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@${Orgs.TELEMARK}.no")
                }
            }

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.continueFromOrgSelector(page, Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.submit(page)

        PwFlow.continueFromIdpSelector(page, Idps.ENTRA_TELEMARK)
        PwAutoLogin.login(page, user)

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login cannot proceed without selecting organization and shows validation error`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)

        val continueButton = page.getByTestId("continue-with-selected-org")

        continueButton.click()

        val validationError = page.locator("#org-not-selected-error")
        assertThat(validationError).isVisible()
        assertThat(validationError).hasAttribute("role", "alert")
        assertThat(validationError)
            .containsText("Please select an affiliation to continue")
        assertThat(page.locator("#selected_org"))
            .hasAttribute("aria-invalid", "true")
    }

    @TestTemplate
    fun `invalid identity provider displays FlaisError`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.continueFromOrgSelector(page, Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.submit(page)

        val idpButton = page.locator("button[name='identity_provider']").first()
        assertThat(idpButton).isVisible()

        idpButton.evaluate(
            "(button, alias) => { button.value = alias; }",
            Idps.NON_EXISTING,
        )
        idpButton.click()

        assertThat(page.locator("#login-title"))
            .hasText("We are sorry...")

        assertThat(page.getByText("We could not complete your sign-in"))
            .isVisible()

        val errorMessage = page.locator("#kc-error-message")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).hasAttribute("role", "alert")
        assertThat(errorMessage)
            .hasText("Could not find the selected identity provider")
    }

    @TestTemplate
    fun `unknown client displays FlaisError without return link`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = "non-existing-client")

        assertThat(page.locator("#login-title"))
            .hasText("We are sorry...")

        val errorMessage = page.locator("#kc-error-message")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).hasAttribute("role", "alert")
        assertThat(errorMessage).hasText("Client not found.")

        assertThat(page.locator("#backToApplication")).hasCount(0)
    }

    @TestTemplate
    fun `back to application navigates to client base URL`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        val testClientId = "flais-error-return-${UUID.randomUUID()}"
        val applicationUrl =
            "${env.flaisKeycloakDemoUrl().trimEnd('/')}/theme-error-return"

        val (admin, realm) = KcAdminClient.connect(env, "external")

        admin.use {
            val client =
                ClientRepresentation().apply {
                    clientId = testClientId
                    protocol = "openid-connect"
                    isEnabled = true
                    isPublicClient = true
                    isStandardFlowEnabled = true
                    baseUrl = applicationUrl
                    redirectUris =
                        listOf(
                            Uris.redirectCallback(env.flaisKeycloakDemoUrl()),
                        )
                }

            val clientUuid =
                realm.clients().create(client).use { response ->
                    CreatedResponseUtil.getCreatedId(response)
                }

            try {
                page.route(applicationUrl) { route ->
                    route.fulfill(
                        Route
                            .FulfillOptions()
                            .setStatus(200)
                            .setContentType("text/html")
                            .setBody(
                                "<!doctype html><html><body>" +
                                    "<h1>Application landing page</h1>" +
                                    "</body></html>",
                            ),
                    )
                }

                PwFlow.navigateToLogin(env, page, clientId = testClientId)
                PwFlow.continueFromOrgSelector(
                    page,
                    Orgs.TELEMARK_DISPLAY_NAME,
                )
                PwFlow.submit(page)

                val idpButton =
                    page.locator("button[name='identity_provider']").first()

                assertThat(idpButton).isVisible()
                idpButton.evaluate(
                    "(button, alias) => { button.value = alias; }",
                    Idps.NON_EXISTING,
                )
                idpButton.click()

                assertThat(page.locator("#kc-error-message"))
                    .hasText("Could not find the selected identity provider")

                val backToApplication = page.locator("#backToApplication")

                assertThat(backToApplication).isVisible()
                assertThat(backToApplication)
                    .hasAttribute("href", applicationUrl)

                backToApplication.click()

                assertThat(page).hasURL(applicationUrl)
                assertThat(page.locator("h1"))
                    .hasText("Application landing page")
            } finally {
                realm.clients().get(clientUuid).remove()
            }
        }
    }

    @TestTemplate
    fun `logout displays FlaisLogoutConfirm without return link`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        navigateToLogout(env, page)

        assertThat(page.locator("#login-title"))
            .hasText("Sign out")

        assertThat(page.getByText("Are you sure you want to sign out?"))
            .isVisible()

        val logoutConfirm = page.locator("#kc-logout-confirm")
        assertThat(logoutConfirm).isVisible()

        val logoutButton = logoutConfirm.locator("#kc-logout")
        assertThat(logoutButton).isVisible()
        assertThat(logoutButton).isEnabled()
        assertThat(logoutButton).hasText("Yes")

        assertThat(page.locator("#backToApplication")).hasCount(0)
    }

    @TestTemplate
    fun `confirming logout displays FlaisInfo`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
        scimUsers: List<ScimUser>,
    ) {
        val page = session.page
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@${Orgs.ROGALAND}.no")
                }
            }

        PwFlow.navigateToLogin(
            env,
            page,
            clientId = Clients.FLAIS_KEYCLOAK_DEMO,
        )
        PwFlow.continueFromOrgSelector(page, Orgs.ROGALAND_DISPLAY_NAME)
        PwFlow.submit(page)
        PwAutoLogin.login(page, user)

        assertCallback(env, page)

        navigateToLogout(env, page)

        assertThat(page.locator("#kc-logout-confirm")).isVisible()
        page.locator("#kc-logout").click()

        assertThat(page.locator("#login-title"))
            .hasText("You are logged out")

        assertThat(page.locator("#kc-info-message"))
            .isVisible()

        assertThat(page.locator("#kc-info-message > p"))
            .hasText("You are logged out")

        assertThat(page.locator("#kc-logout-confirm")).hasCount(0)
        assertThat(page.locator("#kc-error-message")).hasCount(0)
    }

    @TestTemplate
    fun `confirmation offers yes and no and no asks again on next login`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        openRememberConfirmation(env, page)

        assertThat(page.locator("#login-title")).hasText("Remember your organization?")
        assertThat(page.getByTestId("remember-org-yes")).hasText("Yes")
        assertThat(page.getByTestId("remember-org-no")).hasText("No")
        assertThat(page.locator("input[type='checkbox']")).hasCount(0)
        PwFlow.continueFromOrgRemember(page)
        assertThat(page.locator("input[name='username']")).isVisible()
        assertTrue(session.context().cookies().none { it.name == cookieName(Clients.FLAIS_KEYCLOAK_DEMO) })

        openRememberConfirmation(env, page)
        assertThat(page.locator("#login-title")).hasText("Remember your organization?")
    }

    @TestTemplate
    fun `yes remembers organization and matching selection skips confirmation`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        openRememberConfirmation(env, page)
        PwFlow.continueFromOrgRemember(page, rememberOrg = true)
        assertEquals(
            Orgs.TELEMARK,
            session
                .context()
                .cookies()
                .single { it.name == cookieName(Clients.FLAIS_KEYCLOAK_DEMO) }
                .value,
        )

        PwFlow.navigateToLogin(env, page, Clients.FLAIS_KEYCLOAK_DEMO)
        assertThat(page.locator("#selected_org")).hasValue(Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.submit(page)
        PwFlow.continueFromIdpSelector(page, Idps.ENTRA_TELEMARK, hasRememberOrg = false)

        assertThat(page.locator("input[name='username']")).isVisible()
        assertThat(page.getByTestId("remember-org-form")).hasCount(0)
    }

    @TestTemplate
    fun `changing organization asks again and no clears the remembered choice`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        openRememberConfirmation(env, page)
        PwFlow.continueFromOrgRemember(page, rememberOrg = true)

        openRememberConfirmation(env, page, orgName = Orgs.ROGALAND_DISPLAY_NAME)
        assertThat(page.locator("#login-title")).hasText("Remember your organization?")
        PwFlow.continueFromOrgRemember(page)
        assertTrue(session.context().cookies().none { it.name == cookieName(Clients.FLAIS_KEYCLOAK_DEMO) })

        PwFlow.navigateToLogin(env, page, Clients.FLAIS_KEYCLOAK_DEMO)
        assertThat(page.locator("#selected_org")).hasValue("")
    }

    @TestTemplate
    fun `clients remember different organizations and clearing one preserves the other`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        val firstClient = Clients.FLAIS_KEYCLOAK_DEMO
        val secondClient = Clients.FLAIS_KEYCLOAK_DEMO_ENTRA
        openRememberConfirmation(env, page, clientId = firstClient)
        PwFlow.continueFromOrgRemember(page, rememberOrg = true)

        PwFlow.navigateToLogin(env, page, secondClient)
        assertThat(page.locator("#selected_org")).hasValue("")
        PwFlow.continueFromOrgSelector(page, Orgs.ROGALAND_DISPLAY_NAME)
        PwFlow.submit(page, hasRememberOrg = false)
        assertThat(page.locator("#login-title")).hasText("Remember your organization?")
        PwFlow.continueFromOrgRemember(page, rememberOrg = true)

        PwFlow.navigateToLogin(env, page, firstClient)
        assertThat(page.locator("#selected_org")).hasValue(Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.navigateToLogin(env, page, secondClient)
        assertThat(page.locator("#selected_org")).hasValue(Orgs.ROGALAND_DISPLAY_NAME)
        PwFlow.continueFromOrgSelector(page, Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.submit(page)
        PwFlow.continueFromIdpSelector(page, Idps.ENTRA_TELEMARK, hasRememberOrg = false)
        assertThat(page.locator("#login-title")).hasText("Remember your organization?")
        PwFlow.continueFromOrgRemember(page)

        assertTrue(session.context().cookies().none { it.name == cookieName(secondClient) })
        assertEquals(
            Orgs.TELEMARK,
            session
                .context()
                .cookies()
                .single { it.name == cookieName(firstClient) }
                .value,
        )
        PwFlow.navigateToLogin(env, page, firstClient)
        assertThat(page.locator("#selected_org")).hasValue(Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.navigateToLogin(env, page, secondClient)
        assertThat(page.locator("#selected_org")).hasValue("")
    }

    @TestTemplate
    fun `invalid remember choice displays FlaisError`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page
        openRememberConfirmation(env, page)
        val button = page.getByTestId("remember-org-yes")
        button.evaluate("button => { button.value = 'invalid'; }")
        button.click()

        assertThat(page.locator("#kc-error-message"))
            .hasText("Choose whether to remember your organization to continue")
        assertThat(page.locator("#kc-error-message")).hasAttribute("role", "alert")
        assertThat(page.getByTestId("remember-org-form")).hasCount(0)
        assertTrue(session.context().cookies().none { it.name == cookieName(Clients.FLAIS_KEYCLOAK_DEMO) })
    }

    private fun openRememberConfirmation(
        env: KcEnvironment,
        page: Page,
        clientId: String = Clients.FLAIS_KEYCLOAK_DEMO,
        orgName: String = Orgs.TELEMARK_DISPLAY_NAME,
    ) {
        PwFlow.navigateToLogin(env, page, clientId)
        PwFlow.continueFromOrgSelector(page, orgName)
        PwFlow.submit(page, hasRememberOrg = false)
        if (orgName == Orgs.TELEMARK_DISPLAY_NAME) {
            PwFlow.continueFromIdpSelector(page, Idps.ENTRA_TELEMARK, hasRememberOrg = false)
        }
        assertThat(page.getByTestId("remember-org-form")).isVisible()
    }

    private fun assertCallback(
        env: KcEnvironment,
        page: Page,
    ) {
        assertThat(page).hasURL(
            Pattern.compile(Uris.redirectCallback(env.flaisKeycloakDemoUrl())),
        )
    }

    private fun navigateToLogout(
        env: KcEnvironment,
        page: Page,
    ) {
        val baseUrl = env.keycloakServiceUrl().trimEnd('/')

        page.navigate(
            "$baseUrl/realms/external/protocol/openid-connect/logout" +
                "?client_id=${Clients.FLAIS_KEYCLOAK_DEMO}",
        )
    }
}

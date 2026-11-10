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

        assertThat(page.locator("#org-not-selected-error"))
            .hasText("Please select an affiliation to continue")
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
            .hasText("Log out")

        assertThat(page.getByText("Do you want to log out?"))
            .isVisible()

        val logoutConfirm = page.locator("#kc-logout-confirm")
        assertThat(logoutConfirm).isVisible()

        val logoutButton = logoutConfirm.locator("#kc-logout")
        assertThat(logoutButton).isVisible()
        assertThat(logoutButton).isEnabled()
        assertThat(logoutButton).hasText("Log out")

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

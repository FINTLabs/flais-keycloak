package no.novari.test.system.e2e.theme

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import no.novari.test.common.annotations.PwTest
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.pw.PwEnvironment
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Uris
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.system.utils.PwAutoLogin
import no.novari.test.system.utils.PwFlow
import org.junit.jupiter.api.TestTemplate
import java.util.regex.Pattern

@PwTest
class FlaisLoginThemeTest {
    @TestTemplate
    fun `login works with org selector`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.continueFromOrgSelector(page, Orgs.ROGALAND_DISPLAY_NAME)
        PwFlow.submit(page)

        PwAutoLogin.login(page, Users.ALICE_ROGALAND, Users.PASSWORD)

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login works with idp selector`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.continueFromOrgSelector(page, Orgs.TELEMARK_DISPLAY_NAME)
        PwFlow.submit(page)

        PwFlow.continueFromIdpSelector(page, Idps.ENTRA_TELEMARK)
        PwAutoLogin.login(page, Users.ALICE_TELEMARK, Users.PASSWORD)

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login cannot proceed without selecting organization`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = Clients.FLAIS_KEYCLOAK_DEMO)
        PwFlow.submit(page)

        assertThat(page).hasURL(
            Pattern.compile(env.keycloakServiceUrl()),
        )
    }

    private fun assertCallback(
        env: KcEnvironment,
        page: Page,
    ) {
        assertThat(page).hasURL(
            Pattern.compile(Uris.redirectCallback(env.flaisKeycloakDemoUrl())),
        )
    }
}

package no.novari.test.system.e2e.theme

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import no.novari.test.common.annotations.PwTest
import no.novari.test.common.utils.kc.KcEnvironment
import no.novari.test.common.utils.pw.PwEnvironment
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

        PwFlow.navigateToLogin(env, page, clientId = "flais-keycloak-demo")
        PwFlow.continueFromOrgSelector(page, "Rogaland")
        PwFlow.submit(page)

        PwAutoLogin.login(page, "alice.basic@rogaland.no", "password")

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login works with idp selector`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = "flais-keycloak-demo")
        PwFlow.continueFromOrgSelector(page, "Telemark")
        PwFlow.submit(page)

        PwFlow.continueFromIdpSelector(page, "entra-telemark")
        PwAutoLogin.login(page, "alice.basic@telemark.no", "password")

        assertCallback(env, page)
    }

    @TestTemplate
    fun `login cannot proceed without selecting organization`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = "flais-keycloak-demo")
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
            Pattern.compile("${env.flaisKeycloakDemoUrl()}/callback"),
        )
    }
}

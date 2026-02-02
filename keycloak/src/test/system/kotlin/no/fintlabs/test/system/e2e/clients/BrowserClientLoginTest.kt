package no.fintlabs.test.system.e2e.clients

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import no.fintlabs.test.common.annotations.PwTest
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.pw.PwEnvironment
import no.fintlabs.test.system.utils.PwFlow
import org.junit.jupiter.api.TestTemplate

@PwTest
class BrowserClientLoginTest {
    @TestTemplate
    fun `non existent client should return page with error message - Client not found`(
        env: KcEnvironment,
        session: PwEnvironment.Session,
    ) {
        val page = session.page

        PwFlow.navigateToLogin(env, page, clientId = "invalid")

        val errorMessage = page.locator("#kc-error-message .instruction")
        assertThat(errorMessage).isVisible()
        assertThat(errorMessage).hasText("Client not found.")
    }
}

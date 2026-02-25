package no.fintlabs.test.system.utils

import com.microsoft.playwright.Page

/**
 * Helper for performing automatic login flows in Playwright-based system tests.
 * Encapsulates the local login steps by filling credentials and submitting the
 * authentication forms to simplify test setup.
 */
object PwAutoLogin {
    fun login(
        page: Page,
        username: String? = null,
        password: String? = null,
    ) {
        page.waitForTimeout(3000.0)

        loginLocal(
            page,
            requireNotNull(username) { "localUsername is required" },
            requireNotNull(password) { "localPassword is required" },
        )
    }

    private fun loginLocal(
        page: Page,
        username: String,
        password: String,
    ) {
        page.locator("input#ak-identifier-input").fill(username)

        page.locator("button[type=\"submit\"]").click()

        page.locator("input#ak-stage-password-input").fill(password)

        page.locator("button[type=\"submit\"]").click()
    }
}

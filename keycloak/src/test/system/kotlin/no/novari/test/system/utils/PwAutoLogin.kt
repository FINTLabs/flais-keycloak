package no.novari.test.system.utils

import com.microsoft.playwright.Page

/** Select a fixture identity at the mock IDP. There is no password authentication. */
object PwAutoLogin {
    fun login(
        page: Page,
        username: String,
    ) {
        page.locator("input[name=\"username\"]").fill(username)
        page.locator("button[type=\"submit\"]").click()
    }
}

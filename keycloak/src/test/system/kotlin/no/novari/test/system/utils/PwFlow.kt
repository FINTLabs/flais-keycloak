package no.novari.test.system.utils

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.utils.KcUrl

/**
 * Utility functions to simplify Playwright flows in system tests.
 */
object PwFlow {
    fun navigateToLogin(
        env: KcEnvironment,
        page: Page,
        clientId: String,
    ) {
        val httpUrl = KcUrl.authUrl(env, clientId).first
        page.navigate(httpUrl.toString())
    }

    fun continueFromOrgSelector(
        page: Page,
        orgName: String,
    ) {
        page.locator("#selected_org").click()

        page
            .getByRole(AriaRole.LISTBOX)
            .getByRole(
                AriaRole.OPTION,
                Locator.GetByRoleOptions().setName(orgName),
            ).click()
    }

    fun continueFromIdpSelector(
        page: Page,
        idpAlias: String,
        hasRememberOrg: Boolean = true,
        rememberOrg: Boolean = false,
    ) {
        page.locator("button[name='identity_provider'][value='$idpAlias']").click()
        continueRememberStep(page, hasRememberOrg, rememberOrg)
    }

    fun continueFromOrgRemember(
        page: Page,
        rememberOrg: Boolean = false,
    ) {
        val buttonId = if (rememberOrg) "remember-org-yes" else "remember-org-no"
        page.getByTestId(buttonId).click()
        page.locator("input[name='username'], #kc-error-message").first().waitFor()
    }

    fun submit(
        page: Page,
        hasRememberOrg: Boolean = true,
        rememberOrg: Boolean = false,
    ) {
        val orgSubmit = page.getByTestId("continue-with-selected-org")
        val isOrgSelection = orgSubmit.isVisible
        val submitButton = if (isOrgSelection) orgSubmit else page.locator("button[type=\"submit\"]")
        assertThat(submitButton).isEnabled()
        submitButton.click()
        if (isOrgSelection) {
            continueRememberStep(page, hasRememberOrg, rememberOrg, hasIdpSelector = true)
        }
    }

    private fun continueRememberStep(
        page: Page,
        hasRememberOrg: Boolean,
        rememberOrg: Boolean,
        hasIdpSelector: Boolean = false,
    ) {
        val nextPage = "[data-testid='remember-org-form'], input[name='username'], #kc-error-message"
        val selector = if (hasIdpSelector) "button[name='identity_provider'], $nextPage" else nextPage
        page.locator(selector).first().waitFor()
        if (hasRememberOrg && page.getByTestId("remember-org-form").isVisible) {
            continueFromOrgRemember(page, rememberOrg)
        }
    }
}

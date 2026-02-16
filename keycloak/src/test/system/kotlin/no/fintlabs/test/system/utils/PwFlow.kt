package no.fintlabs.test.system.utils

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.kc.KcUrl

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
        val listBoxWrapper =
            page
                .locator("input[placeholder=\"Choose affiliation\"]")
                .locator("..")
                .locator("..")

        listBoxWrapper.getByRole(AriaRole.BUTTON).click()

        listBoxWrapper
            .getByRole(AriaRole.LISTBOX)
            .getByRole(
                AriaRole.BUTTON,
                Locator.GetByRoleOptions().setName(orgName),
            ).click()
    }

    fun continueFromIdpSelector(
        page: Page,
        idpAlias: String,
    ) {
        page.locator("button[name='identity_provider'][value='$idpAlias']").click()
    }

    fun submit(page: Page) {
        page.locator("button[type=\"submit\"]").click()
    }
}

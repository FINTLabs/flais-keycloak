package no.novari.test.system.utils

import com.microsoft.playwright.Page
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.novari.test.common.utils.ScimFlow.ScimUser

/** Select an identity at the mock IDP. There is no password authentication. */
object PwAutoLogin {
    fun login(
        page: Page,
        username: String,
    ) {
        page.locator("input[name=\"username\"]").fill(username)
        page.locator("button[type=\"submit\"]").click()
    }

    fun login(
        page: Page,
        user: ScimUser,
    ) {
        val claims =
            buildJsonObject {
                put("oid", user.externalId)
                put(
                    "roles",
                    JsonArray(
                        user.roles.orEmpty().mapNotNull { role ->
                            role.value?.let { JsonPrimitive(it) }
                        },
                    ),
                )
            }

        page.locator("input[name=\"username\"]").fill(user.userName)
        page.locator("[name=\"claims\"]").fill(claims.toString())
        page.locator("button[type=\"submit\"]").click()
    }
}

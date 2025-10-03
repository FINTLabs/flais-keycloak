package utils.utils.no.fintlabs.application

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.utils.no.fintlabs.utils.WithKeycloakEnv

class KeycloakHealth : WithKeycloakEnv() {

    private val client = OkHttpClient()

    @Test
    fun `keycloak ready endpoint returns 200`() {
        val url = "${env.keycloakManagementUrl()}/health/ready"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
        }
    }
}
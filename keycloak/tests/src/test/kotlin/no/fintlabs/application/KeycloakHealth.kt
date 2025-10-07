package no.fintlabs.application

import no.fintlabs.utils.KeycloakComposeEnvironment
import no.fintlabs.utils.KeycloakEnvExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KeycloakEnvExtension::class)
class KeycloakHealth  {

    private val client = OkHttpClient()

    @Test
    fun `keycloak ready endpoint returns 200`(env: KeycloakComposeEnvironment) {
        val url = "${env.keycloakManagementUrl()}/health/ready"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
        }
    }
}
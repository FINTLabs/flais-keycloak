package no.novari.test.integration.application.kc

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class KcHealthTest {
    private val client = OkHttpClient()

    @Test
    fun `keycloak ready endpoint returns 200`(env: KcEnvironment) {
        val url = "${env.keycloakManagementUrl()}/health/ready"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
        }
    }
}

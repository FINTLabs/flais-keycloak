package no.fintlabs.test.integration.application.kc

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
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

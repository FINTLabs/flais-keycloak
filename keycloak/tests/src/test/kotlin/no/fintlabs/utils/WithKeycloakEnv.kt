package utils.utils.no.fintlabs.utils

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WithKeycloakEnv {
    protected lateinit var env: KeycloakComposeEnvironment

    @BeforeAll
    fun startEnv() {
        env = KeycloakComposeEnvironment()
        env.start()
    }

    @AfterAll
    fun stopEnv() {
        env.close()
    }
}
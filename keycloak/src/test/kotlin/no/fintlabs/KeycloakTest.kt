import dasniko.testcontainers.keycloak.KeycloakContainer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomImageKeycloakTest {

  companion object {
    private val image = System.getProperty("keycloak.image", "flais-keycloak:test")
    @JvmStatic
    @Container
    val keycloak =
            KeycloakContainer(image).withAdminUsername("admin").withAdminPassword("tops3cr3t")
  }

  @Test
  fun `server should be up`() {
    val base = keycloak.authServerUrl // resolved mapped port
    assertTrue(base.contains("http"), "Got auth server URL: $base")
  }
}

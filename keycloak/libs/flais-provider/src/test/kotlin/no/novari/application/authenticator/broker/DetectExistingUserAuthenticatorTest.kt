package no.novari.application.authenticator.broker

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import jakarta.ws.rs.core.Response
import no.novari.authenticator.broker.DetectExistingUserAuthenticator
import no.novari.authenticator.broker.DetectExistingUserAuthenticatorConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.authentication.AuthenticationFlowException
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator.EXISTING_USER_INFO
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.events.Errors
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.AuthenticatorConfigModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserProvider
import org.keycloak.sessions.AuthenticationSessionModel

class DetectExistingUserAuthenticatorTest {
    private lateinit var authenticator: DetectExistingUserAuthenticator
    private lateinit var context: AuthenticationFlowContext
    private lateinit var authSession: AuthenticationSessionModel
    private lateinit var session: KeycloakSession
    private lateinit var realm: RealmModel
    private lateinit var users: UserProvider
    private lateinit var config: AuthenticatorConfigModel
    private lateinit var serializedCtx: SerializedBrokeredIdentityContext
    private lateinit var brokerContext: BrokeredIdentityContext
    private lateinit var form: LoginFormsProvider
    private lateinit var response: Response

    private lateinit var authNotes: MutableMap<String, String>

    private var existingUsername = "test-user"

    @BeforeEach
    fun setUp() {
        val idpConfig = mockk<org.keycloak.models.IdentityProviderModel>()

        authenticator = DetectExistingUserAuthenticator()
        context = mockk(relaxed = true)
        authSession = mockk()
        session = mockk()
        realm = mockk()
        users = mockk()
        config = mockk()
        serializedCtx = mockk()
        brokerContext = mockk(relaxed = true)
        form = mockk()
        response = mockk()
        authNotes = mutableMapOf()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticatorConfig } returns config
        every { context.form() } returns form
        every { session.users() } returns users

        every { config.config } returns
            mutableMapOf("errorMessageKey" to "userNotAllowed")

        every { realm.isRegistrationEmailAsUsername } returns false
        every { realm.isLoginWithEmailAllowed } returns false
        every { brokerContext.modelUsername } returns existingUsername
        every { brokerContext.email } returns null

        every { users.getUserByUsername(realm, any()) } returns null

        every { authSession.getAuthNote(any()) } answers {
            authNotes[firstArg<String>()]
        }
        every { authSession.removeAuthNote(any()) } answers {
            authNotes.remove(firstArg<String>())
        }
        every { authSession.setAuthNote(any(), any()) } answers {
            authNotes[firstArg<String>()] = secondArg<String>()
        }

        every { form.setError(any<String>()) } returns form
        every { form.createErrorPage(Response.Status.FORBIDDEN) } returns response

        mockkStatic(SerializedBrokeredIdentityContext::class)

        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                BROKERED_CONTEXT_NOTE,
            )
        } returns serializedCtx

        every {
            serializedCtx.deserialize(session, authSession)
        } returns brokerContext

        every { brokerContext.idpConfig } returns idpConfig
        every { idpConfig.isEnabled } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SerializedBrokeredIdentityContext::class)
    }

    private fun mockExistingUser(): String {
        val user = mockk<UserModel>()

        every { user.id } returns "user-id-1"
        every { user.username } returns "test-user"
        every { users.getUserByUsername(realm, "test-user") } returns user

        return ExistingUserInfo(
            "user-id-1",
            UserModel.USERNAME,
            "test-user",
        ).serialize()
    }

    private fun verifyRejected(errorMessageKey: String = "userNotAllowed") {
        verify(exactly = 1) { context.event.error(Errors.USER_NOT_FOUND) }
        verify(exactly = 1) { form.setError(errorMessageKey) }
        verify(exactly = 1) {
            form.createErrorPage(Response.Status.FORBIDDEN)
        }
        verify(exactly = 1) {
            context.failure(AuthenticationFlowError.INVALID_USER, response)
        }
        verify(exactly = 0) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
        verify(exactly = 0) {
            authSession.setAuthNote(EXISTING_USER_INFO, any())
        }

        assertNull(authNotes[EXISTING_USER_INFO])
    }

    private fun verifyConfigurationFailure() {
        val exception =
            assertThrows<AuthenticationFlowException> {
                authenticator.authenticate(context)
            }

        assertEquals(AuthenticationFlowError.INTERNAL_ERROR, exception.error)
        assertEquals(
            DetectExistingUserAuthenticatorConfig.MISSING_ERROR_MESSAGE_KEY_MESSAGE,
            exception.message,
        )

        verify(exactly = 1) {
            authSession.removeAuthNote(EXISTING_USER_INFO)
        }
        verify(exactly = 0) {
            users.getUserByUsername(any(), any())
        }
        verify(exactly = 0) { context.success() }
        verify(exactly = 0) {
            context.failure(any<AuthenticationFlowError>(), any<Response>())
        }
    }

    @Test
    fun `authenticate - missing authenticator config should throw`() {
        every { context.authenticatorConfig } returns null

        verifyConfigurationFailure()
    }

    @Test
    fun `authenticate - missing error message key should throw`() {
        every { config.config } returns mutableMapOf<String, String>()

        verifyConfigurationFailure()
    }

    @Test
    fun `authenticate - whitespace error message key should throw`() {
        every { config.config } returns
            mutableMapOf("errorMessageKey" to "   ")

        verifyConfigurationFailure()
    }

    @Test
    fun `authenticate - empty string error message key should throw`() {
        every { config.config } returns
            mutableMapOf("errorMessageKey" to "")

        verifyConfigurationFailure()
    }

    @Test
    fun `authenticate - missing username should reject`() {
        every { brokerContext.modelUsername } returns null

        authenticator.authenticate(context)

        verifyRejected()
        verify(exactly = 0) {
            users.getUserByUsername(any(), any())
        }
    }

    @Test
    fun `authenticate - whitespace username should reject`() {
        every { brokerContext.modelUsername } returns "   "

        authenticator.authenticate(context)

        verifyRejected()
        verify(exactly = 0) {
            users.getUserByUsername(any(), any())
        }
    }

    @Test
    fun `authenticate - empty string username should reject`() {
        every { brokerContext.modelUsername } returns ""

        authenticator.authenticate(context)

        verifyRejected()
        verify(exactly = 0) {
            users.getUserByUsername(any(), any())
        }
    }

    @Test
    fun `authenticate - existing user not found should reject`() {
        authenticator.authenticate(context)

        verify(exactly = 1) {
            users.getUserByUsername(realm, "test-user")
        }
        verifyRejected()
    }

    @Test
    fun `authenticate - error message key should be trimmed`() {
        every { config.config } returns
            mutableMapOf("errorMessageKey" to "  userNotAllowed  ")

        authenticator.authenticate(context)

        verifyRejected("userNotAllowed")
    }

    @Test
    fun `authenticate - existing user should store user info and succeed`() {
        val expectedUserInfo = mockExistingUser()

        authenticator.authenticate(context)

        assertEquals(expectedUserInfo, authNotes[EXISTING_USER_INFO])

        verifyOrder {
            authSession.removeAuthNote(EXISTING_USER_INFO)
            users.getUserByUsername(realm, "test-user")
            authSession.setAuthNote(EXISTING_USER_INFO, expectedUserInfo)
            context.success()
        }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) {
            context.failure(any<AuthenticationFlowError>(), any<Response>())
        }
        verify(exactly = 0) { context.form() }
    }

    @Test
    fun `authenticate - rejected retry should clear previously detected user`() {
        val expectedUserInfo = mockExistingUser()

        authenticator.authenticate(context)

        assertEquals(expectedUserInfo, authNotes[EXISTING_USER_INFO])

        every { users.getUserByUsername(realm, "test-user") } returns null

        authenticator.authenticate(context)

        assertNull(authNotes[EXISTING_USER_INFO])

        verify(exactly = 2) {
            authSession.removeAuthNote(EXISTING_USER_INFO)
        }
        verify(exactly = 1) {
            authSession.setAuthNote(EXISTING_USER_INFO, expectedUserInfo)
        }
        verify(exactly = 1) { context.success() }
        verify(exactly = 1) { context.event.error(Errors.USER_NOT_FOUND) }
        verify(exactly = 1) {
            context.failure(AuthenticationFlowError.INVALID_USER, response)
        }
    }

    @Test
    fun `action - existing user should store user info and succeed`() {
        val expectedUserInfo = mockExistingUser()

        authenticator.action(context)

        assertEquals(expectedUserInfo, authNotes[EXISTING_USER_INFO])

        verify(exactly = 1) {
            authSession.removeAuthNote(EXISTING_USER_INFO)
        }
        verify(exactly = 1) {
            authSession.setAuthNote(EXISTING_USER_INFO, expectedUserInfo)
        }
        verify(exactly = 1) { context.success() }
    }

    @Test
    fun `action - existing user not found should reject`() {
        authenticator.action(context)

        verifyRejected()
    }
}

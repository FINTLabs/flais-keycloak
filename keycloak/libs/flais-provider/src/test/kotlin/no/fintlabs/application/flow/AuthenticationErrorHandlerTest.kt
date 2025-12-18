package no.fintlabs.application.flow

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.Response
import no.fintlabs.flow.AuthenticationErrorHandler
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.forms.login.LoginFormsProvider

class AuthenticationErrorHandlerTest {
    @Test
    fun `failure sets error and challenges with error page`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val formProvider = mockk<LoginFormsProvider>()
        val response = mockk<Response>()
        val errorMessage = "This is an error message"

        every { context.form() } returns formProvider
        every { formProvider.setError(errorMessage) } returns formProvider
        every { formProvider.createErrorPage(Response.Status.OK) } returns response

        with(AuthenticationErrorHandler) {
            context.failure(errorMessage)
        }

        verify(exactly = 1) { context.form() }
        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { formProvider.createErrorPage(Response.Status.OK) }
        verify(exactly = 1) { context.challenge(response) }

        confirmVerified(context, formProvider)
    }
}

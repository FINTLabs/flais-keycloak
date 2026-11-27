package no.novari.application.authenticator.org

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import no.novari.authenticator.org.OrgRememberUiAuthenticator
import no.novari.authenticator.org.OrgRememberUiAuthenticatorConfig
import no.novari.provider.ClientOrgAccessProvider
import no.novari.utils.OrgRememberCookie.cookieName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.http.HttpRequest
import org.keycloak.http.HttpResponse
import org.keycloak.models.AuthenticatorConfigModel
import org.keycloak.models.KeycloakContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.sessions.AuthenticationSessionModel
import org.keycloak.utils.SecureContextResolver
import java.net.URI
import java.util.concurrent.TimeUnit

class OrgRememberUiAuthenticatorTest {
    private lateinit var authenticator: OrgRememberUiAuthenticator
    private lateinit var context: AuthenticationFlowContext
    private lateinit var authSession: AuthenticationSessionModel
    private lateinit var session: KeycloakSession
    private lateinit var orgAccess: ClientOrgAccessProvider
    private lateinit var httpRequest: HttpRequest
    private lateinit var httpResponse: HttpResponse
    private lateinit var form: LoginFormsProvider
    private lateinit var formResponse: Response
    private lateinit var organization: OrganizationModel

    @BeforeEach
    fun setUp() {
        authenticator = OrgRememberUiAuthenticator()
        context = mockk()
        authSession = mockk(relaxed = true)
        session = mockk()
        orgAccess = mockk()
        httpRequest = mockk(relaxed = true)
        httpResponse = mockk(relaxed = true)
        form = mockk()
        formResponse = mockk()
        organization = mockk()
        val keycloakContext = mockk<KeycloakContext>()

        every { organization.id } returns "org-id-1"
        every { organization.alias } returns "org-1"
        every { authSession.client.clientId } returns "client-a"
        every { authSession.getAuthNote(Details.ORG_ID) } returns organization.id
        every { context.authenticatorConfig } returns null
        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { context.httpRequest } returns httpRequest
        every { context.uriInfo } returns mockk(relaxed = true)
        every { context.uriInfo.baseUri } returns URI("https://keycloak.example/auth/")
        every { context.realm } returns mockk(relaxed = true)
        every { context.realm.name } returns "external"
        every { context.form() } returns form
        every { context.success() } just Runs
        every { context.challenge(formResponse) } just Runs
        every { session.getProvider(ClientOrgAccessProvider::class.java) } returns orgAccess
        every { session.context } returns keycloakContext
        every { keycloakContext.httpResponse } returns httpResponse
        every { orgAccess.getAllowedOrgs(context) } returns listOf(organization)
        every { httpRequest.httpHeaders.cookies } returns emptyMap()
        every { httpRequest.decodedFormParameters } returns MultivaluedHashMap()
        every { form.createForm("flais-org-remember.ftl") } returns formResponse
        every { form.setError(any()) } returns form
        every { form.createErrorPage(Response.Status.OK) } returns formResponse
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["org-2", "org-id-1"])
    fun `authenticate - missing or different remembered alias shows confirmation`(rememberedOrg: String?) {
        every { httpRequest.httpHeaders.cookies } returns
            if (rememberedOrg == null) {
                emptyMap()
            } else {
                mapOf(cookieName("client-a") to Cookie.Builder(cookieName("client-a")).value(rememberedOrg).build())
            }

        authenticator.authenticate(context)

        verify(exactly = 1) { form.createForm("flais-org-remember.ftl") }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { context.success() }
        verify(exactly = 0) { httpResponse.setCookieIfAbsent(any()) }
    }

    @Test
    fun `authenticate - matching remembered alias skips confirmation`() {
        every { httpRequest.httpHeaders.cookies } returns
            mapOf(cookieName("client-a") to Cookie.Builder(cookieName("client-a")).value(organization.alias).build())

        authenticator.authenticate(context)

        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.form() }
        verify(exactly = 0) { context.challenge(any()) }
        verify(exactly = 0) { httpResponse.setCookieIfAbsent(any()) }
    }

    @Test
    fun `authenticate - matching alias remembered for another client does not skip confirmation`() {
        every { httpRequest.httpHeaders.cookies } returns
            mapOf(cookieName("client-b") to Cookie.Builder(cookieName("client-b")).value(organization.alias).build())

        authenticator.authenticate(context)

        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { context.success() }
    }

    @ParameterizedTest
    @CsvSource("authenticate,", "authenticate,removed-org", "action,", "action,removed-org")
    fun `missing or unauthorized selected organization shows error even with matching cookie`(
        entryPoint: String,
        selectedOrgId: String?,
    ) {
        every { authSession.getAuthNote(Details.ORG_ID) } returns selectedOrgId
        every { httpRequest.httpHeaders.cookies } returns
            mapOf(cookieName("client-a") to Cookie.Builder(cookieName("client-a")).value(organization.alias).build())
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply { putSingle("remember_org", true.toString()) }

        if (entryPoint == "authenticate") authenticator.authenticate(context) else authenticator.action(context)

        verify(exactly = 1) { form.setError("No permitted organization selected") }
        verify(exactly = 1) { form.createErrorPage(Response.Status.OK) }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { form.createForm(any()) }
        verify(exactly = 0) { context.success() }
        verify(exactly = 0) { httpResponse.setCookieIfAbsent(any()) }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["yes", "no", "on", "0", "1", "TRUE", "FALSE", "invalid"])
    fun `action - missing or invalid boolean shows error without changing cookie`(choice: String?) {
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                if (choice != null) putSingle("remember_org", choice)
            }

        authenticator.action(context)

        verify(exactly = 1) { form.setError("Choose whether to remember your organization to continue") }
        verify(exactly = 1) { form.createErrorPage(Response.Status.OK) }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { form.createForm(any()) }
        verify(exactly = 0) { context.success() }
        verify(exactly = 0) { httpResponse.setCookieIfAbsent(any()) }
    }

    @ParameterizedTest
    @CsvSource("true,true,client-a", "true,false,client-b", "false,true,client-a", "false,false,client-b")
    fun `action - boolean choice writes or clears only the current client cookie`(
        rememberOrg: Boolean,
        secure: Boolean,
        clientId: String,
    ) {
        every { authSession.client.clientId } returns clientId
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply { putSingle("remember_org", rememberOrg.toString()) }
        val cookie = slot<NewCookie>()
        mockkStatic(SecureContextResolver::class)
        try {
            every { SecureContextResolver.isSecureContext(session) } returns secure

            authenticator.action(context)

            verify(exactly = 1) { httpResponse.setCookieIfAbsent(capture(cookie)) }
            assertEquals(cookieName(clientId), cookie.captured.name)
            assertEquals(if (rememberOrg) organization.alias else "", cookie.captured.value)
            val defaultMaxAge =
                TimeUnit.DAYS.toSeconds(OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS.toLong()).toInt()
            assertEquals(if (rememberOrg) defaultMaxAge else 0, cookie.captured.maxAge)
            assertEquals("/auth/realms/external", cookie.captured.path)
            assertEquals(secure, cookie.captured.isSecure)
            assertTrue(cookie.captured.isHttpOnly)
            assertEquals(NewCookie.SameSite.LAX, cookie.captured.sameSite)
            verify(exactly = 1) { context.success() }
            verify(exactly = 0) { context.challenge(any()) }
        } finally {
            unmockkStatic(SecureContextResolver::class)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 7, 60, 24855])
    fun `action - configured lifetime is applied in days`(days: Int) {
        every { context.authenticatorConfig } returns
            AuthenticatorConfigModel().apply {
                config = mapOf(OrgRememberUiAuthenticatorConfig.COOKIE_MAX_AGE_DAYS to days.toString())
            }

        assertRememberedCookieMaxAge(TimeUnit.DAYS.toSeconds(days.toLong()).toInt())
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["invalid", "0", "-1", "1.5", "24856", "9999999999999999999999"])
    fun `action - missing or invalid lifetime uses configured default`(days: String?) {
        every { context.authenticatorConfig } returns
            AuthenticatorConfigModel().apply {
                config =
                    if (days == null) {
                        emptyMap()
                    } else {
                        mapOf(OrgRememberUiAuthenticatorConfig.COOKIE_MAX_AGE_DAYS to days)
                    }
            }

        assertRememberedCookieMaxAge(
            TimeUnit.DAYS.toSeconds(OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS.toLong()).toInt(),
        )
    }

    private fun assertRememberedCookieMaxAge(expectedMaxAge: Int) {
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply { putSingle("remember_org", true.toString()) }
        val cookie = slot<NewCookie>()
        mockkStatic(SecureContextResolver::class)
        try {
            every { SecureContextResolver.isSecureContext(session) } returns true

            authenticator.action(context)

            verify(exactly = 1) { httpResponse.setCookieIfAbsent(capture(cookie)) }
            assertEquals(expectedMaxAge, cookie.captured.maxAge)
            verify(exactly = 1) { context.success() }
        } finally {
            unmockkStatic(SecureContextResolver::class)
        }
    }
}

package no.fintlabs.application.mapper

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.fintlabs.mapper.OrgSpecificAttributeMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserSessionModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper
import org.keycloak.representations.AccessToken
import org.keycloak.representations.IDToken
import java.util.stream.Stream

class OrgSpecificAttributeMapperTest {
    private lateinit var mapper: OrgSpecificAttributeMapper
    private lateinit var session: KeycloakSession
    private lateinit var orgProvider: OrganizationProvider
    private lateinit var user: UserModel
    private lateinit var userSession: UserSessionModel
    private lateinit var clientSessionCtx: ClientSessionContext

    val defaultTokenClaimName = "claim"
    val defaultAttributeName = "ORGANIZATION_NUMBER"
    val defaultAttributeValue = "123456789"

    @BeforeEach
    fun setUp() {
        mapper = OrgSpecificAttributeMapper()

        session = mockk<KeycloakSession>(relaxed = true)
        orgProvider = mockk<OrganizationProvider>(relaxed = true)
        user = mockk<UserModel>(relaxed = true)
        userSession = mockk<UserSessionModel>(relaxed = true)
        clientSessionCtx = mockk<ClientSessionContext>(relaxed = true)

        every { user.username } returns "alice"
        every { userSession.user } returns user
        every { session.getAttribute(any()) } returns null
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
    }

    private fun mappingModel(
        orgAttributeName: String? = defaultAttributeName,
        tokenClaimName: String = defaultTokenClaimName,
        includeInAccessToken: Boolean = true,
        includeInIdToken: Boolean = true,
    ): ProtocolMapperModel =
        ProtocolMapperModel().apply {
            config = HashMap()

            orgAttributeName?.let { config["orgAttribute"] = it }
            config[OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME] = tokenClaimName

            if (includeInAccessToken) {
                config[OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN] = "true"
            } else {
                config.remove(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN)
            }

            if (includeInIdToken) {
                config[OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN] = "true"
            } else {
                config.remove(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN)
            }
        }

    private fun orgWithAttr(
        attrName: String = defaultAttributeName,
        value: String = defaultAttributeValue,
    ): OrganizationModel =
        mockk<OrganizationModel>().also { org ->
            every { org.attributes } returns mapOf(attrName to listOf(value))
        }

    @Test
    fun `missing orgAttribute config should not map anything`() {
        every { orgProvider.getByMember(user) } returns Stream.empty()

        val token = AccessToken()
        val model = mappingModel(orgAttributeName = null)

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
    }

    @Test
    fun `include in access token is false should not map anything`() {
        val org = orgWithAttr()
        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = AccessToken()
        val model =
            mappingModel(
                includeInAccessToken = false,
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 0) { orgProvider.getByMember(any()) }
    }

    @Test
    fun `include in id token is false should not map anything`() {
        val org = orgWithAttr()
        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = IDToken()
        val model =
            mappingModel(
                orgAttributeName = "ORGANIZATION_NUMBER",
                includeInAccessToken = false,
                includeInIdToken = false,
            )

        mapper.transformIDToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 0) { orgProvider.getByMember(any()) }
    }

    @Test
    fun `user has no organizations should not map`() {
        every { orgProvider.getByMember(user) } returns Stream.empty()

        val token = AccessToken()
        val model = mappingModel()

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
    }

    @Test
    fun `org missing attribute should not map`() {
        val org = mockk<OrganizationModel>()
        every { org.attributes } returns emptyMap()

        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = AccessToken()
        val model = mappingModel()

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
    }

    @Test
    fun `user has org with attribute should map attribute value for access token to Token Claim Name`() {
        val org = orgWithAttr()
        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = AccessToken()
        val model = mappingModel(includeInIdToken = false)

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertEquals(defaultAttributeValue, token.otherClaims[defaultTokenClaimName])
        verify(exactly = 1) { orgProvider.getByMember(user) }
    }

    @Test
    fun `user has org with attribute should map attribute value for id token to Token Claim Name`() {
        val org = orgWithAttr()
        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = IDToken()
        val model =
            mappingModel(
                includeInAccessToken = false,
            )

        mapper.transformIDToken(token, model, session, userSession, clientSessionCtx)

        assertEquals(defaultAttributeValue, token.otherClaims[defaultTokenClaimName])
        verify(exactly = 1) { orgProvider.getByMember(user) }
    }

    @Test
    fun `multiple orgs with attribute should take first value in stream order`() {
        val org1 = orgWithAttr(value = "111")
        val org2 = orgWithAttr(value = "222")

        every { orgProvider.getByMember(user) } returns Stream.of(org1, org2)

        val token = AccessToken()
        val model = mappingModel()

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertEquals("111", token.otherClaims[defaultTokenClaimName])
    }

    @Test
    fun `token claim name controls output key`() {
        val org = orgWithAttr()
        every { orgProvider.getByMember(user) } returns Stream.of(org)

        val token = AccessToken()
        val model = mappingModel(tokenClaimName = "orgNo")

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertEquals(defaultAttributeValue, token.otherClaims["orgNo"])
        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
    }
}

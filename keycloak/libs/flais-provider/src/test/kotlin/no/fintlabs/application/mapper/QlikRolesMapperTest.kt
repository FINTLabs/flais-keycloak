package no.fintlabs.application.mapper

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import no.fintlabs.mapper.QlikRolesMapper
import no.fintlabs.mapper.QlikRolesMapper.Companion.CFG_PASSTHROUGH_COUNTIES
import no.fintlabs.mapper.QlikRolesMapper.Companion.CFG_ROLES_ATTRIBUTE
import no.fintlabs.mapper.QlikRolesMapper.Companion.CFG_TENANT_ATTRIBUTE
import no.fintlabs.mapper.QlikRolesMapper.Companion.CFG_TENANT_COUNTY_MAP
import no.fintlabs.utils.OrgAttributeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.Constants
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper
import org.keycloak.representations.AccessToken
import org.keycloak.representations.IDToken

class QlikRolesMapperTest {
    private lateinit var mapper: QlikRolesMapper
    private lateinit var session: KeycloakSession
    private lateinit var user: UserModel
    private lateinit var userSession: UserSessionModel
    private lateinit var clientSessionCtx: ClientSessionContext

    private val defaultTokenClaimName = "qlik"
    private val defaultTenantAttributeName = "TENANT_ID"
    private val defaultRolesAttributeName = "roles"
    private val defaultTenantId = "a3d9c6f2-7b41-4e2a-9f8b-1c0e4f6b2a91"
    private val defaultPassthroughCounty =
        """
        novari
        frid
        """.trimIndent()
    private val defaultTenantMap =
        """
        a3d9c6f2-7b41-4e2a-9f8b-1c0e4f6b2a91=1
        c84f1a9e-2d6b-43c1-b5a2-9e7d4f0c8a3b=22
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        mapper = QlikRolesMapper()

        session = mockk(relaxed = true)
        user = mockk(relaxed = true)
        userSession = mockk(relaxed = true)
        clientSessionCtx = mockk(relaxed = true)

        every { user.username } returns "alice"
        every { userSession.user } returns user
        every { session.getAttribute(Constants.USE_LIGHTWEIGHT_ACCESS_TOKEN_ENABLED) } returns null
        every { session.context.client.getAttribute(Constants.USE_LIGHTWEIGHT_ACCESS_TOKEN_ENABLED) } returns null

        mockkObject(OrgAttributeUtils)
        every {
            OrgAttributeUtils.getAttributeValue(session, user, defaultTenantAttributeName)
        } returns defaultTenantId
    }

    private fun mappingModel(
        tenantMap: String? = defaultTenantMap,
        passthroughCounty: String? = defaultPassthroughCounty,
        tenantAttribute: String? = defaultTenantAttributeName,
        rolesAttribute: String? = defaultRolesAttributeName,
        tokenClaimName: String = defaultTokenClaimName,
        includeInAccessToken: Boolean = true,
        includeInIdToken: Boolean = true,
        multivalued: Boolean = true,
    ): ProtocolMapperModel =
        ProtocolMapperModel().apply {
            config = HashMap()

            tenantMap?.let { config[CFG_TENANT_COUNTY_MAP] = it }
            passthroughCounty?.let { config[CFG_PASSTHROUGH_COUNTIES] = it }
            tenantAttribute?.let { config[CFG_TENANT_ATTRIBUTE] = it }
            rolesAttribute?.let { config[CFG_ROLES_ATTRIBUTE] = it }

            config[OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME] = tokenClaimName
            config[org.keycloak.protocol.ProtocolMapperUtils.MULTIVALUED] = multivalued.toString()

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

    @Test
    fun `missing tenantAttribute config should not map anything`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("a"))

        val token = AccessToken()
        val model = mappingModel(tenantAttribute = null)

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 0) { OrgAttributeUtils.getAttributeValue(any(), any(), any()) }
    }

    @Test
    fun `tenant attribute present but getAttributeValue returns null should not map`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("a"))
        every { OrgAttributeUtils.getAttributeValue(session, user, defaultTenantAttributeName) } returns null

        val token = AccessToken()
        val model = mappingModel()

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 1) { OrgAttributeUtils.getAttributeValue(session, user, defaultTenantAttributeName) }
    }

    @Test
    fun `include in access token is false should not map anything`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("a"))

        val token = AccessToken()
        val model = mappingModel(includeInAccessToken = false)

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 0) { OrgAttributeUtils.getAttributeValue(any(), any(), any()) }
    }

    @Test
    fun `include in id token is false should not map anything`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("a"))

        val token = IDToken()
        val model = mappingModel(includeInAccessToken = false, includeInIdToken = false)

        mapper.transformIDToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
        verify(exactly = 0) { OrgAttributeUtils.getAttributeValue(any(), any(), any()) }
    }

    @Test
    fun `tenant id not found in tenantToCountyMap should not map`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("role1"))
        every {
            OrgAttributeUtils.getAttributeValue(session, user, defaultTenantAttributeName)
        } returns "tenant-does-not-exist"

        val token = AccessToken()
        val model = mappingModel()

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))
    }

    @Test
    fun `tenantToCountyMap ignores malformed lines and still maps when match exists`() {
        every { user.attributes } returns
            mapOf(defaultRolesAttributeName to listOf("https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak"))

        val token = AccessToken()
        val model =
            mappingModel(
                tenantMap =
                    """
                    this-is-not-valid
                    a3d9c6f2-7b41-4e2a-9f8b-1c0e4f6b2a91=1
                    too=many=equals
                    """.trimIndent(),
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        val roles = token.otherClaims[defaultTokenClaimName]
        assertEquals(
            listOf(
                "1_https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
                "https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
            ),
            roles,
        )
    }

    @Test
    fun `county in passthrough list should return roles unchanged`() {
        every { user.attributes } returns mapOf(defaultRolesAttributeName to listOf("r1", "r2"))

        val token = AccessToken()
        val model =
            mappingModel(
                tenantMap = "$defaultTenantId=novari",
                """
                novari
                frid
                """.trimIndent(),
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        val roles = token.otherClaims[defaultTokenClaimName]
        assertEquals(listOf("r1", "r2"), roles)
    }

    @Test
    fun `county not in passthrough list should prefix roles and also include original, only for https roles`() {
        every { user.attributes } returns
            mapOf(
                defaultRolesAttributeName to
                    listOf(
                        "https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-fagopplaring-MV",
                        "_internal",
                        "1_https://",
                        "editor",
                    ),
            )

        val token = AccessToken()
        val model =
            mappingModel(
                tenantMap = "$defaultTenantId=1",
                passthroughCounty =
                    """
                    novari
                    frid
                    """.trimIndent(),
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        val roles = token.otherClaims[defaultTokenClaimName]
        assertEquals(
            listOf(
                "1_https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-fagopplaring-MV",
                "https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-fagopplaring-MV",
            ),
            roles,
        )
    }

    @Test
    fun `token claim name controls output key`() {
        every { user.attributes } returns
            mapOf(defaultRolesAttributeName to listOf("https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak"))

        val token = AccessToken()
        val model =
            mappingModel(
                tokenClaimName = "claim",
                tenantMap = "$defaultTenantId=1",
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        assertFalse(token.otherClaims.containsKey(defaultTokenClaimName))

        val roles = token.otherClaims["claim"]
        assertEquals(
            listOf(
                "1_https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
                "https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
            ),
            roles,
        )
    }

    @Test
    fun `null passthroughCounty config should continue as normal`() {
        every { user.attributes } returns
            mapOf(defaultRolesAttributeName to listOf("https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak"))

        val token = AccessToken()
        val model =
            mappingModel(
                tenantMap = "$defaultTenantId=1",
                passthroughCounty = null,
            )

        mapper.transformAccessToken(token, model, session, userSession, clientSessionCtx)

        val roles = token.otherClaims[defaultTokenClaimName]
        assertEquals(
            listOf(
                "1_https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
                "https://role-catalog.vigoiks.no/vigo/qlik/qliksense/fk-inntak",
            ),
            roles,
        )
    }
}

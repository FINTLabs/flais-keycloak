package no.fintlabs.mapper

import no.fintlabs.utils.OrgAttributeUtils.getAttributeValue
import org.jboss.logging.Logger
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.IDToken

class OrgSpecificAttributeMapper :
    AbstractOIDCProtocolMapper(),
    OIDCAccessTokenMapper,
    OIDCIDTokenMapper {
    private val logger: Logger = Logger.getLogger(OrgSpecificAttributeMapper::class.java)

    companion object {
        const val PROVIDER_ID = "org-specific-attribute-mapper"

        private const val CFG_ORG_ATTRIBUTE = "orgAttribute"

        private val CONFIG_PROPERTIES: List<ProviderConfigProperty> =
            buildList {
                add(
                    ProviderConfigProperty().apply {
                        name = CFG_ORG_ATTRIBUTE
                        label = "Attribute name"
                        helpText =
                            "Name of the attribute on the Organization that will be collected into the claim."
                        type = ProviderConfigProperty.STRING_TYPE
                        isRequired = true
                    },
                )

                OIDCAttributeMapperHelper.addTokenClaimNameConfig(this)
                OIDCAttributeMapperHelper.addJsonTypeConfig(this)
                OIDCAttributeMapperHelper.addIncludeInTokensConfig(
                    this,
                    OrgSpecificAttributeMapper::class.java,
                )
            }
    }

    override fun getDisplayCategory(): String = "Organization"

    override fun getDisplayType(): String = "Organization specific attribute"

    override fun getHelpText(): String = "Add claim for a configured organization attribute."

    override fun getId(): String = PROVIDER_ID

    override fun getConfigProperties(): List<ProviderConfigProperty> = CONFIG_PROPERTIES

    override fun setClaim(
        token: IDToken,
        mappingModel: ProtocolMapperModel,
        userSession: UserSessionModel,
        keycloakSession: KeycloakSession,
        clientSessionCtx: ClientSessionContext,
    ) {
        logger.debugf("Adding org claim to token for %s", userSession.user.username)

        val value =
            getAttributeValue(
                keycloakSession,
                userSession.user,
                mappingModel.config[CFG_ORG_ATTRIBUTE] ?: return,
            )
        if (value != null) OIDCAttributeMapperHelper.mapClaim(token, mappingModel, value)
    }
}

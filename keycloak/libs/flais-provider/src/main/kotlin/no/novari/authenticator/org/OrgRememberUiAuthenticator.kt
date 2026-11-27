package no.novari.authenticator.org

import no.novari.flow.AuthenticationErrorHandler.failure
import no.novari.provider.ClientOrgAccessProvider
import no.novari.utils.OrgRememberCookie
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.events.Details
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

class OrgRememberUiAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgRememberUiAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val organization =
            selectedOrganization(context) ?: return context.failure("No permitted organization selected")
        val rememberedOrg = OrgRememberCookie.read(context)
        if (rememberedOrg == organization.alias) {
            logger.debugf(
                "Remembered organization matches selection, skipping confirmation. client=%s org=%s",
                context.authenticationSession.client.clientId,
                organization.alias,
            )
            context.success()
            return
        }
        logger.debugf(
            "Showing remember organization confirmation. client=%s org=%s cookiePresent=%s",
            context.authenticationSession.client.clientId,
            organization.alias,
            rememberedOrg != null,
        )
        showConfirmation(context)
    }

    override fun action(context: AuthenticationFlowContext) {
        val organization =
            selectedOrganization(context) ?: return context.failure("No permitted organization selected")
        val rememberOrg =
            context.httpRequest.decodedFormParameters
                .getFirst("remember_org")
                ?.toBooleanStrictOrNull()
        if (rememberOrg == null) {
            logger.warnf(
                "Missing or invalid remember organization choice. client=%s org=%s",
                context.authenticationSession.client.clientId,
                organization.alias,
            )
            context.failure("Choose whether to remember your organization to continue")
            return
        }

        val cookie =
            OrgRememberCookie.build(
                context,
                organizationAlias = if (rememberOrg) organization.alias else null,
                maxAge = if (rememberOrg) getCookieMaxAge(context) else 0,
            )

        context.session.context.httpResponse
            .setCookieIfAbsent(cookie)
        logger.debugf(
            "Remember organization cookie updated. client=%s path=%s maxAge=%d secure=%s",
            context.authenticationSession.client.clientId,
            cookie.path,
            cookie.maxAge,
            cookie.isSecure,
        )
        context.success()
    }

    private fun getCookieMaxAge(context: AuthenticationFlowContext): Int {
        val configuredDays =
            context.authenticatorConfig
                ?.config
                ?.get(OrgRememberUiAuthenticatorConfig.COOKIE_MAX_AGE_DAYS)
        val days = configuredDays?.toIntOrNull()
        if (days != null && days in 1..Int.MAX_VALUE / SECONDS_PER_DAY) {
            return days * SECONDS_PER_DAY
        }
        if (configuredDays != null) {
            logger.warnf(
                "Invalid remember organization lifetime, using default. client=%s configuredDays=%s defaultDays=%d",
                context.authenticationSession.client.clientId,
                configuredDays,
                OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS,
            )
        }
        return OrgRememberUiAuthenticatorConfig.DEFAULT_COOKIE_MAX_AGE_DAYS * SECONDS_PER_DAY
    }

    private fun selectedOrganization(context: AuthenticationFlowContext): OrganizationModel? {
        val selectedOrg = context.authenticationSession.getAuthNote(Details.ORG_ID)
        val organization =
            context.session
                .getProvider(ClientOrgAccessProvider::class.java)
                .getAllowedOrgs(context)
                .find { it.id == selectedOrg }

        if (organization == null) {
            logger.warnf(
                "Remember organization step has no permitted selection. client=%s selectedOrgId=%s",
                context.authenticationSession.client.clientId,
                selectedOrg,
            )
        }

        return organization
    }

    private fun showConfirmation(context: AuthenticationFlowContext) {
        context.challenge(context.form().createForm("flais-org-remember.ftl"))
    }

    override fun requiresUser(): Boolean = false

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel,
    ): Boolean = true

    override fun setRequiredActions(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel,
    ) = Unit

    override fun close() = Unit

    private companion object {
        const val SECONDS_PER_DAY = 24 * 60 * 60
    }
}

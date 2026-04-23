package no.novari.application.attributes

import no.novari.attributes.ClientPermissionAttribute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ClientPermissionAttribute {
    @Test
    fun `PERMISSION_WHITELISTED_ORGANIZATIONS has expected value`() {
        assertEquals(
            "permission.whitelisted.organizations",
            ClientPermissionAttribute.PERMISSION_WHITELISTED_ORGANIZATIONS,
        )
    }

    @Test
    fun `PERMISSION_BLACKLISTED_ORGANIZATIONS has expected value`() {
        assertEquals(
            "permission.blacklisted.organizations",
            ClientPermissionAttribute.PERMISSION_BLACKLISTED_ORGANIZATIONS,
        )
    }

    @Test
    fun `constants are not blank`() {
        assertFalse(ClientPermissionAttribute.PERMISSION_WHITELISTED_ORGANIZATIONS.isBlank())
        assertFalse(ClientPermissionAttribute.PERMISSION_BLACKLISTED_ORGANIZATIONS.isBlank())
    }
}

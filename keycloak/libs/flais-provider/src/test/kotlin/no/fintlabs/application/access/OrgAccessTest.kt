package no.fintlabs.application.access

import no.fintlabs.access.OrgAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OrgAccessTest {
    @Test
    fun `ORG_LOGO_ATTRIBUTE has expected value`() {
        assertEquals(
            "attributes.logo",
            OrgAccess.ORG_LOGO_ATTRIBUTE,
        )
    }

    @Test
    fun `ORG_LOGO_ATTRIBUTE is not blank`() {
        assertFalse(OrgAccess.ORG_LOGO_ATTRIBUTE.isBlank())
    }
}

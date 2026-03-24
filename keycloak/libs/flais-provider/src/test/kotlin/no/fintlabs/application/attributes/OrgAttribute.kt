package no.fintlabs.application.attributes

import no.fintlabs.attributes.OrgAttribute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OrgAttribute {
    @Test
    fun `ORGANIZATION_LOGO has expected value`() {
        assertEquals(
            "ORGANIZATION_LOGO",
            OrgAttribute.ORGANIZATION_LOGO,
        )
    }

    @Test
    fun `ORGANIZATION_LOGO is not blank`() {
        assertFalse(OrgAttribute.ORGANIZATION_LOGO.isBlank())
    }
}

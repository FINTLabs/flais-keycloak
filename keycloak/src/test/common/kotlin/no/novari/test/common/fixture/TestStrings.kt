package no.novari.test.common.fixture

object TestStrings {
    object Clients {
        const val FLAIS_KEYCLOAK_DEMO = "flais-keycloak-demo"
        const val FLAIS_KEYCLOAK_DEMO_ENTRA = "flais-keycloak-demo-entra"
        const val FLAIS_KEYCLOAK_DEMO_IDPORTEN = "flais-keycloak-demo-idporten"
        const val FLAIS_KEYCLOAK_DEMO_INVALID = "flais-keycloak-demo-invalid"
        const val FLAIS_KEYCLOAK_DEMO_NOT_TELEMARK = "flais-keycloak-demo-not-telemark"
        const val FLAIS_KEYCLOAK_DEMO_TELEMARK = "flais-keycloak-demo-telemark"
        const val QLIK = "qlik"
    }

    object Orgs {
        const val IDPORTEN = "idporten"
        const val INNLANDET = "innlandet"
        const val INVALID = "invalid-org"
        const val NON_EXISTING = "nonExistingOrg"
        const val ROGALAND = "rogaland"
        const val ROGALAND_DISPLAY_NAME = "Rogaland"
        const val TELEMARK = "telemark"
        const val TELEMARK_DISPLAY_NAME = "Telemark"
    }

    object Idps {
        const val ENTRA_ROGALAND = "entra-rogaland"
        const val ENTRA_TELEMARK = "entra-telemark"
        const val ENTRA_TELEMARK_ALT = "entra-telemark-alt"
        const val IDPORTEN = "idporten"
        const val NON_EXISTING = "non-existing-idp"

        fun entra(orgAlias: String) = "entra-$orgAlias"
    }

    object Users {
        const val ALICE_FIRST_NAME = "Alice"
        const val ALICE_ROGALAND = "alice.basic@rogaland.no"
        const val ALICE_TELEMARK = "alice.basic@telemark.no"
        const val JON_FIRST_NAME = "Jon"
        const val JON_ROGALAND = "jon.basic@rogaland.no"
        const val JON_TELEMARK = "jon.basic@telemark.no"
        const val SCIMVERIFY_FIRST_NAME = "Scimverify"
        const val BASIC_LAST_NAME = "Basic"
        const val PASSWORD = "password"

        fun alice(orgAlias: String) = "alice.basic@$orgAlias.no"
        fun qlikBasic(orgAlias: String) = "qlik.basic@$orgAlias.no"
        fun scimVerify(orgAlias: String) = "scimverify.user@$orgAlias.no"
    }

    object Realms {
        const val EXTERNAL = "external"
    }

    object Scopes {
        const val PROFILE_EMAIL = "profile email"
        const val PROFILE_EMAIL_ORGANIZATION = "profile email organization"
        const val ORGANIZATION = "organization"
    }

    object Pages {
        const val ERROR = "error"
        const val FLAIS_ORG_IDP_SELECTOR = "flais-org-idp-selector"
        const val FLAIS_ORG_SELECTOR = "flais-org-selector"
    }
}

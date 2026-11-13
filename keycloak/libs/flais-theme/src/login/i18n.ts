/* eslint-disable @typescript-eslint/no-unused-vars */
import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

/** @see: https://docs.keycloakify.dev/features/i18n */
const { useI18n, ofTypeI18n } = i18nBuilder
  .withThemeName<ThemeName>()
  .withCustomTranslations({
    no: {
      errorSubtitle: "Vi kunne ikke fullføre innloggingen din",
      chooseIdp: "Velg identitetsleverandør",
      chooseIdpSubtitle: "Velg metoden du ønsker å bruke for å logge inn",
      chooseOrg: "Velg tilhørighet",
      chooseOrgSubtitle: "Velg hvilken organisasjon du ønsker å logge inn hos",
      continue: "Fortsett",
      logoutConfirmTitle: "Logg ut",
      logoutConfirmHeader: "Vil du logge ut?",
      doLogout: "Logg ut",
      proceedWithAction: "Klikk her for å fortsette",
      successLogout: "Du er logget ut",
      otherSignInOptions: "Andre påloggingsalternativer",
      noResult: "Ingen treff",
      userNotProvisioned:
        "Din konto har ikke blitt provisionert enda. Det kan ta opp til 1 time før kontoen blir tilgjengelig etter at du har blitt lagt til. Vennligst kontakt din administrator.",
      closeList: "Lukk liste",
      openList: "Åpne liste",
      chooseOrgRequired: "Velg en tilhørighet for å fortsette",
    },
    en: {
      errorSubtitle: "We could not complete your sign-in",
      chooseIdp: "Choose identity provider",
      chooseIdpSubtitle: "Choose the method you want to use to log in",
      chooseOrg: "Choose affiliation",
      chooseOrgSubtitle: "Choose the organization you want to log in with",
      continue: "Continue",
      logoutConfirmTitle: "Log out",
      logoutConfirmHeader: "Do you want to log out?",
      doLogout: "Log out",
      proceedWithAction: "Click here to continue",
      successLogout: "You are logged out",
      otherSignInOptions: "Other sign-in options",
      noResult: "No result",
      userNotProvisioned:
        "Your account has not yet been provisioned. It may take up to 1 hour for your account to become available after you have been added. Please contact your administrator.",
      closeList: "Close list",
      openList: "Open list",
      chooseOrgRequired: "Please select an affiliation to continue",
    },
  })
  .build();

type I18n = typeof ofTypeI18n;

export { useI18n, type I18n };

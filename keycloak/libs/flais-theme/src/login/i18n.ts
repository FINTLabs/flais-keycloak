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
      chooseOrgSubtitle:
        "Søk etter eller velg organisasjonen du tilhører for å logge inn.",
      searchOrgPlaceholder: "Søk, f.eks. Telemark",
      signInWithOrg: "Logg inn",
      continue: "Fortsett",
      logoutConfirmTitle: "Logg ut",
      logoutConfirmHeader: "Er du sikker på at du vil logge ut?",
      logoutCancel: "Nei, tilbake til applikasjonen",
      doLogout: "Logg ut",
      proceedWithAction: "Klikk her for å fortsette",
      successLogout: "Du er logget ut",
      otherSignInOptions: "Andre påloggingsalternativer",
      noResult: "Ingen treff",
      closeList: "Lukk liste",
      openList: "Åpne liste",
      chooseOrgRequired: "Velg en tilhørighet for å fortsette",
    },
    en: {
      errorSubtitle: "We could not complete your sign-in",
      chooseIdp: "Choose identity provider",
      chooseIdpSubtitle: "Choose the method you want to use to log in",
      chooseOrg: "Choose affiliation",
      chooseOrgSubtitle:
        "Search for your organization or choose it from the list to sign in.",
      searchOrgPlaceholder: "Search, e.g. Telemark",
      signInWithOrg: "Sign in",
      continue: "Continue",
      logoutConfirmTitle: "Sign out",
      logoutConfirmHeader: "Are you sure you want to sign out?",
      logoutCancel: "No, back to application",
      doLogout: "Sign out",
      proceedWithAction: "Click here to continue",
      successLogout: "You are logged out",
      otherSignInOptions: "Other sign-in options",
      noResult: "No result",
      closeList: "Close list",
      openList: "Open list",
      chooseOrgRequired: "Please select an affiliation to continue",
    },
  })
  .build();

type I18n = typeof ofTypeI18n;

export { useI18n, type I18n };

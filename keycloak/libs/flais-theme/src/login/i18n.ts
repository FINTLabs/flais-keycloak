/* eslint-disable @typescript-eslint/no-unused-vars */
import { i18nBuilder } from 'keycloakify/login'
import type { ThemeName } from '../kc.gen'

/** @see: https://docs.keycloakify.dev/features/i18n */
const { useI18n, ofTypeI18n } = i18nBuilder
  .withThemeName<ThemeName>()
  .withCustomTranslations({
    no: {
      chooseIdp: 'Velg identitetsleverandør',
      chooseIdpSubtitle: 'Velg metoden du ønsker å bruke for å logge inn',
      chooseOrg: 'Velg tilhøringhet',
      chooseOrgSubtitle: 'Velg hvilken organisasjon du ønsker å logge inn hos',
      continue: 'Fortsett',
      rememberMe: 'Husk meg',
    },
    en: {
      chooseIdp: 'Choose identity provider',
      chooseIdpSubtitle: 'Choose the method you want to use to log in',
      chooseOrg: 'Choose affiliation',
      chooseOrgSubtitle: 'Choose the organization you want to log in with',
      continue: 'Continue',
      rememberMe: 'Remember me',
    },
  })
  .build()

type I18n = typeof ofTypeI18n

export { useI18n, type I18n }

import type { DeepPartial } from 'keycloakify/tools/DeepPartial'
import type { KcContext } from './KcContext'
import KcPage from './KcPage'
import { createGetKcContextMock } from 'keycloakify/login/KcContext'
import type { KcContextExtension, KcContextExtensionPerPage } from './KcContext'
import { themeNames, kcEnvDefaults } from '../kc.gen'

const kcContextExtension: KcContextExtension = {
  themeName: themeNames[0],
  properties: {
    ...kcEnvDefaults,
  },
}
const kcContextExtensionPerPage: KcContextExtensionPerPage = {
  'flais-org-selector.ftl': {
    organizations: [
      {
        alias: 'test',
        name: 'Testing',
      },
    ],
    url: {
      registrationAction: '',
    },
  },
  'flais-org-idp-selector.ftl': {
    providers: [
      {
        alias: 'test',
        name: 'Test',
      },
      {
        alias: 'test2',
        name: 'Test 2',
      },
    ],
    url: {
      registrationAction: '',
    },
  },
}

export const { getKcContextMock } = createGetKcContextMock({
  kcContextExtension,
  kcContextExtensionPerPage,
  overrides: {},
  overridesPerPage: {},
})

export function createKcPageStory<PageId extends KcContext['pageId']>(params: {
  pageId: PageId
}) {
  const { pageId } = params

  function KcPageStory(props: {
    kcContext?: DeepPartial<Extract<KcContext, { pageId: PageId }>>
  }) {
    const { kcContext: overrides } = props

    const kcContextMock = getKcContextMock({
      pageId,
      overrides,
    })

    return <KcPage kcContext={kcContextMock} />
  }

  return { KcPageStory }
}

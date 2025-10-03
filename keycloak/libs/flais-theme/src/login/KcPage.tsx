import './main.css'
import { Suspense, lazy } from 'react'
import type { ClassKey } from 'keycloakify/login'
import type { KcContext } from './KcContext'
import { useI18n } from './i18n'
import DefaultPage from 'keycloakify/login/DefaultPage'
import Template from 'keycloakify/login/Template'
import FlaisOrgIdpSelector from './pages/FlaisOrgIdpSelector'
import FlaisOrgSelector from './pages/FlaisOrgSelector'
const UserProfileFormFields = lazy(
  () => import('keycloakify/login/UserProfileFormFields')
)

const doMakeUserConfirmPassword = true

export default function KcPage(props: { kcContext: KcContext }) {
  const { kcContext } = props

  const { i18n } = useI18n({ kcContext })

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case 'flais-org-selector.ftl':
            return <FlaisOrgSelector kcContext={kcContext} />
          case 'flais-org-idp-selector.ftl':
            return <FlaisOrgIdpSelector kcContext={kcContext} />
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                i18n={i18n}
                classes={classes}
                Template={Template}
                doUseDefaultCss={true}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
              />
            )
        }
      })()}
    </Suspense>
  )
}

const classes = {} satisfies { [key in ClassKey]?: string }

import React from 'react'
import logo from '../assets/images/novari_logo.png'

import { KcContext } from '../KcContext.ts'
import { I18n } from '../i18n.ts'
import { PageWrapper } from './components/PageWrapper.tsx'
import { LoginHeader } from './components/LoginHeader.tsx'
import { LoginCard } from './components/LoginCard.tsx'
import { IdpButton } from './components/IdpButton.tsx'

export interface FlaisOrgIdpSelectorProps {
  kcContext: Extract<KcContext, { pageId: 'flais-org-idp-selector.ftl' }>
  i18n: I18n
}

const FlaisOrgIdpSelectorComponent = (props: FlaisOrgIdpSelectorProps) => {
  const { kcContext, i18n } = props
  const { providers, url } = kcContext
  return (
    <PageWrapper>
      <LoginCard logo={logo}>
        <LoginHeader
          title={i18n.msgStr('chooseIdp')}
          subtitle={i18n.msgStr('chooseIdpSubtitle')}
        />
        <form
          className="space-y-5"
          method="POST"
          action={url.registrationAction}
        >
          <div
            className="
                        bg-white shadow-sm
                        border-t border-b border-gray-200
                        divide-y divide-gray-200
                      "
          >
            {providers.map((p) => (
              <IdpButton name={p.name} alias={p.alias} />
            ))}
          </div>
        </form>
      </LoginCard>
    </PageWrapper>
  )
}

export const FlaisOrgIdpSelector = React.memo(FlaisOrgIdpSelectorComponent)

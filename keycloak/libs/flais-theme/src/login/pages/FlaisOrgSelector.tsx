import React from 'react'
import logo from '../assets/images/novari_logo.png'

import { I18n } from '../i18n.ts'
import { RememberMe } from './components/RememberMe.tsx'
import { SubmitButton } from './components/SubmitButton.tsx'
import { PageWrapper } from './components/PageWrapper.tsx'
import { OrgSelect } from './components/OrgSelect.tsx'
import { LoginHeader } from './components/LoginHeader.tsx'
import { LoginCard } from './components/LoginCard.tsx'
import { KcContext } from '../KcContext.ts'

export interface FlaisOrgSelectorProps {
  kcContext: Extract<KcContext, { pageId: 'flais-org-selector.ftl' }>
  i18n: I18n
}

const FlaisOrgSelectorComponent = (props: FlaisOrgSelectorProps) => {
  const { kcContext, i18n } = props
  const { organizations, url } = kcContext

  return (
    <PageWrapper>
      <LoginCard logo={logo}>
        <LoginHeader
          title={i18n.msgStr('chooseOrg')}
          subtitle={i18n.msgStr('chooseOrgSubtitle')}
        />
        <form
          className="space-y-5"
          method="POST"
          action={url.registrationAction}
        >
          <OrgSelect i18n={i18n} organizations={organizations} />
          <RememberMe i18n={i18n} />
          <SubmitButton text={i18n.msgStr('continue').toUpperCase()} />
        </form>
      </LoginCard>
    </PageWrapper>
  )
}

export const FlaisOrgSelector = React.memo(FlaisOrgSelectorComponent)

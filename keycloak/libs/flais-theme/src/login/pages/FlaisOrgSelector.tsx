import type { KcContext } from '../KcContext'

import logo from '../assets/images/novari_logo.png'

import PageWrapper from './components/PageWrapper.tsx'
import LoginCard from './components/LoginCard.tsx'
import OrgSelect from './components/OrgSelect.tsx'
import RememberMe from './components/RememberMe.tsx'
import SubmitButton from './components/SubmitButton.tsx'
import LoginHeader from './components/LoginHeader.tsx'

interface FlaisOrgSelectorProps {
  kcContext: Extract<KcContext, { pageId: 'flais-org-selector.ftl' }>
}

export default function FlaisOrgSelector(props: FlaisOrgSelectorProps) {
  const { kcContext } = props
  const { organizations, url } = kcContext
  return (
    <PageWrapper>
      <LoginCard logo={logo}>
        <LoginHeader
          title={'Velg tilhøringhet'}
          subtitle={'Veld hvilken organisasjon du ønsker å logge inn hos'}
        />
        <form
          className="space-y-5"
          method="POST"
          action={url.registrationAction}
        >
          <OrgSelect organizations={organizations} />
          <RememberMe />
          <SubmitButton />
        </form>
      </LoginCard>
    </PageWrapper>
  )
}

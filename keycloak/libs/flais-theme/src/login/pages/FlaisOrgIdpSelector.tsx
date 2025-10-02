import PageWrapper from './components/PageWrapper.tsx'
import LoginCard from './components/LoginCard.tsx'
import logo from '../assets/images/novari_logo.png'
import LoginHeader from './components/LoginHeader.tsx'
import { KcContext } from '../KcContext.ts'
import IdpButton from './components/IdpButton.tsx'

interface FlaisOrgIdpSelectorProps {
  kcContext: Extract<KcContext, { pageId: 'flais-org-idp-selector.ftl' }>
}

export default function FlaisOrgSelector(props: FlaisOrgIdpSelectorProps) {
  const { kcContext } = props
  const { providers, url } = kcContext
  return (
    <PageWrapper>
      <LoginCard logo={logo}>
        <LoginHeader
          title={'Velg identitetsleverandør'}
          subtitle={'Velg metoden du ønsker å bruke for å logge inn'}
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

import React from "react";
import logo from "../assets/images/novari_logo.png";

import { KcContext } from "../KcContext.ts";
import { I18n } from "../i18n.ts";
import { IdpButton } from "../components/IdpButton.tsx";
import { LoginCard } from "../components/LoginCard.tsx";
import { LoginHeader } from "../components/LoginHeader.tsx";
import { PageWrapper } from "../components/PageWrapper.tsx";

export interface FlaisOrgIdpSelectorProps {
  kcContext: Extract<KcContext, { pageId: "flais-org-idp-selector.ftl" }>;
  i18n: I18n;
}

const FlaisOrgIdpSelectorComponent = ({
  kcContext,
  i18n,
}: FlaisOrgIdpSelectorProps) => {
  const { providers, url } = kcContext;

  return (
    <PageWrapper>
      <LoginCard logo={logo}>
        <LoginHeader
          title={i18n.msgStr("chooseIdp")}
          subtitle={i18n.msgStr("chooseIdpSubtitle")}
        />

        <form
          className="space-y-5"
          method="POST"
          action={url.registrationAction}
        >
          <div className="divide-y divide-gray-200 border-y border-gray-200 bg-white shadow-sm">
            {providers.map((provider) => (
              <IdpButton
                key={`${provider.name}-${provider.alias}`}
                name={provider.name}
                alias={provider.alias}
              />
            ))}
          </div>
        </form>
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisOrgIdpSelector = React.memo(FlaisOrgIdpSelectorComponent);

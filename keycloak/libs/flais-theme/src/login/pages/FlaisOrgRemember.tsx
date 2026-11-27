import React from "react";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { LoginCard } from "../components/LoginCard";
import { LoginHeader } from "../components/LoginHeader";
import { PageWrapper } from "../components/PageWrapper";
import { SubmitButton } from "../components/SubmitButton";

export interface FlaisOrgRememberProps {
  kcContext: Extract<KcContext, { pageId: "flais-org-remember.ftl" }>;
  i18n: I18n;
}

const FlaisOrgRememberComponent = ({
  kcContext,
  i18n,
}: FlaisOrgRememberProps) => (
  <PageWrapper>
    <LoginCard>
      <LoginHeader
        title={i18n.msgStr("rememberOrgTitle")}
        subtitle={i18n.msgStr("rememberOrgSubtitle")}
      />
      <form
        data-testid="remember-org-form"
        method="POST"
        action={kcContext.url.registrationAction}
        className="flex justify-end gap-3 pt-4"
      >
        <button
          type="submit"
          name="remember_org"
          data-testid="remember-org-no"
          value={String(false)}
          className="min-h-11 min-w-24 cursor-pointer rounded-md border border-border-input bg-surface px-5 py-2.5 text-sm font-semibold text-accent hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus-offset"
        >
          {i18n.msgStr("rememberOrgNo")}
        </button>
        <SubmitButton
          name="remember_org"
          data-testid="remember-org-yes"
          value={String(true)}
          text={i18n.msgStr("rememberOrgYes")}
          className="min-w-24"
        />
      </form>
    </LoginCard>
  </PageWrapper>
);

export const FlaisOrgRemember = React.memo(FlaisOrgRememberComponent);

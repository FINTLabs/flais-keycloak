import React from "react";

import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { LoginCard } from "../components/LoginCard";
import { LoginHeader } from "../components/LoginHeader";
import { PageWrapper } from "../components/PageWrapper";
import { SubmitButton } from "../components/SubmitButton";

export interface FlaisLogoutConfirmProps {
  kcContext: Extract<KcContext, { pageId: "logout-confirm.ftl" }>;
  i18n: I18n;
}

const FlaisLogoutConfirmComponent = ({
  kcContext,
  i18n,
}: FlaisLogoutConfirmProps) => {
  const { url, client, logoutConfirm } = kcContext;

  return (
    <PageWrapper>
      <LoginCard>
        <LoginHeader
          title={i18n.msgStr("logoutConfirmTitle")}
          subtitle={i18n.msgStr("logoutConfirmHeader")}
        />

        <div id="kc-logout-confirm" className="space-y-5">
          <form action={url.logoutConfirmAction} method="POST">
            <input
              type="hidden"
              name="session_code"
              value={logoutConfirm.code}
            />
            <SubmitButton
              id="kc-logout"
              name="confirmLogout"
              value={i18n.msgStr("doLogout")}
              text={i18n.msgStr("doLogout")}
            />
          </form>

          {!logoutConfirm.skipLink && client?.baseUrl && (
            <a
              id="backToApplication"
              href={client.baseUrl}
              className="inline-block text-sm text-accent underline underline-offset-4 focus:outline-none focus:ring-2 focus:ring-focus focus:ring-offset-2 focus:ring-offset-focus-offset"
            >
              {i18n.msg("backToApplication")}
            </a>
          )}
        </div>
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisLogoutConfirm = React.memo(FlaisLogoutConfirmComponent);

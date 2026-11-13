import React from "react";

import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { LoginCard } from "../components/LoginCard";
import { PageWrapper } from "../components/PageWrapper";

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
        <header className="space-y-6 text-left">
          <h1
            id="login-title"
            className="text-2xl font-semibold leading-tight text-accent sm:text-3xl"
          >
            {i18n.msgStr("logoutConfirmTitle")}
          </h1>
          <p className="text-base leading-relaxed text-content-muted">
            {i18n.msgStr("logoutConfirmHeader")}
          </p>
        </header>

        <div id="kc-logout-confirm" className="space-y-5">
          <form action={url.logoutConfirmAction} method="POST">
            <input
              type="hidden"
              name="session_code"
              value={logoutConfirm.code}
            />
            <div className="flex items-center justify-between gap-4 pt-3">
              {!logoutConfirm.skipLink && client?.baseUrl && (
                <a
                  id="backToApplication"
                  href={client.baseUrl}
                  className="inline-flex min-h-12 items-center text-sm text-accent underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus-offset"
                >
                  {i18n.msgStr("logoutCancel")}
                </a>
              )}
              <button
                type="submit"
                id="kc-logout"
                name="confirmLogout"
                value={i18n.msgStr("doLogout")}
                className="login-action ml-auto min-h-12"
              >
                {i18n.msgStr("doYes")}
              </button>
            </div>
          </form>
        </div>
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisLogoutConfirm = React.memo(FlaisLogoutConfirmComponent);

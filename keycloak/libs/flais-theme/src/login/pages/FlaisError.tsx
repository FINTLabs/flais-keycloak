import React from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";

import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { LoginCard } from "../components/LoginCard";
import { LoginHeader } from "../components/LoginHeader";
import { PageWrapper } from "../components/PageWrapper";

export interface FlaisErrorProps {
  kcContext: Extract<KcContext, { pageId: "error.ftl" }>;
  i18n: I18n;
}

const FlaisErrorComponent = ({ kcContext, i18n }: FlaisErrorProps) => {
  const { message, client, skipLink } = kcContext;

  return (
    <PageWrapper>
      <LoginCard>
        <LoginHeader
          title={i18n.msgStr("errorTitle")}
          subtitle={i18n.msgStr("errorSubtitle")}
        />

        <div
          id="kc-error-message"
          role="alert"
          className="flex items-start gap-3 wrap-break-word rounded-md border border-error-surface-border border-l-4 border-l-error bg-error-surface p-5 text-base leading-relaxed text-content sm:p-6"
        >
          <svg
            aria-hidden="true"
            className="mt-0.5 h-5 w-5 shrink-0 text-error"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.75}
          >
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v6m0 3v1" strokeLinecap="round" />
          </svg>
          <p
            className="min-w-0 [&_a]:underline [&_a]:underline-offset-2"
            dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
          />
        </div>

        {!skipLink && client?.baseUrl && (
          <a
            id="backToApplication"
            href={client.baseUrl}
            className="login-action"
          >
            {i18n.msg("backToApplication")}
          </a>
        )}
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisError = React.memo(FlaisErrorComponent);

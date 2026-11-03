import React from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import logo from "../assets/images/novari_logo.png";
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
      <LoginCard logo={logo}>
        <LoginHeader
          title={i18n.msgStr("errorTitle")}
          subtitle={i18n.msgStr("errorSubtitle")}
        />

        <div
          id="kc-error-message"
          role="alert"
          className="wrap-break-word rounded-lg border border-red-200 bg-pink p-4 text-sm text-accent sm:text-base"
        >
          <p
            className="[&_a]:underline [&_a]:underline-offset-2"
            dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
          />
        </div>

        {!skipLink && client?.baseUrl && (
          <a
            id="backToApplication"
            href={client.baseUrl}
            className="block w-full rounded-lg bg-primary px-4 py-3 text-center text-sm font-medium uppercase text-white hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 sm:py-3.5 sm:text-base"
          >
            {i18n.msg("backToApplication")}
          </a>
        )}
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisError = React.memo(FlaisErrorComponent);

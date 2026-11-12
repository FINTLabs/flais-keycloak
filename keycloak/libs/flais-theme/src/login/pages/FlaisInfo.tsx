import React from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";

import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { LoginCard } from "../components/LoginCard";
import { LoginHeader } from "../components/LoginHeader";
import { PageWrapper } from "../components/PageWrapper";

export interface FlaisInfoProps {
  kcContext: Extract<KcContext, { pageId: "info.ftl" }>;
  i18n: I18n;
}

const FlaisInfoComponent = ({ kcContext, i18n }: FlaisInfoProps) => {
  const {
    messageHeader,
    message,
    requiredActions,
    skipLink,
    pageRedirectUri,
    actionUri,
    client,
  } = kcContext;
  const { advancedMsgStr, msg } = i18n;
  const href = pageRedirectUri || actionUri || client?.baseUrl;
  const linkLabel =
    !pageRedirectUri && actionUri ? "proceedWithAction" : "backToApplication";
  const summary = [
    message.summary.trim(),
    ...(requiredActions?.length
      ? [
          `<b>${requiredActions
            .map((action) => advancedMsgStr(`requiredAction.${action}`))
            .join(", ")}</b>`,
        ]
      : []),
  ].join(" ");

  return (
    <PageWrapper>
      <LoginCard>
        <LoginHeader
          title={
            <span
              dangerouslySetInnerHTML={{
                __html: kcSanitize(
                  messageHeader
                    ? advancedMsgStr(messageHeader)
                    : message.summary,
                ),
              }}
            />
          }
        />

        <div id="kc-info-message" className="space-y-5">
          <p
            className="wrap-break-word text-sm text-content-muted sm:text-base [&_a]:underline [&_a]:underline-offset-2"
            dangerouslySetInnerHTML={{ __html: kcSanitize(summary) }}
          />

          {!skipLink && href && (
            <a id="backToApplication" href={href} className="login-action">
              {msg(linkLabel)}
            </a>
          )}
        </div>
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisInfo = React.memo(FlaisInfoComponent);

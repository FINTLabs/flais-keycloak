import React, { useMemo, useState } from "react";

import { I18n } from "../i18n.ts";
import { KcContext } from "../KcContext.ts";
import { getLogoUrl } from "../utils/logo-url.ts";
import { LoginCard } from "../components/LoginCard.tsx";
import { LoginHeader } from "../components/LoginHeader.tsx";
import { OrgSelect } from "../components/OrgSelect.tsx";
import { PageWrapper } from "../components/PageWrapper.tsx";
import { SubmitButton } from "../components/SubmitButton.tsx";

export interface FlaisOrgSelectorProps {
  kcContext: Extract<KcContext, { pageId: "flais-org-selector.ftl" }>;
  i18n: I18n;
}

const EXCLUDED_ORG_ALIASES = ["id-porten"];

const sortByName = <T extends { name: string }>(items: T[]) =>
  [...items].sort((a, b) =>
    a.name.localeCompare(b.name, "nb", { sensitivity: "base" }),
  );

const FlaisOrgSelectorComponent = ({
  kcContext,
  i18n,
}: FlaisOrgSelectorProps) => {
  const { organizations, url } = kcContext;
  const [selectedOrg, setSelectedOrg] = useState("");
  const [showOrgError, setShowOrgError] = useState(false);

  const sortedOrganizations = useMemo(
    () => sortByName(organizations),
    [organizations],
  );

  const otherSignInOptions = useMemo(
    () =>
      sortByName(
        organizations.filter((org) => EXCLUDED_ORG_ALIASES.includes(org.alias)),
      ),
    [organizations],
  );

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    if (!selectedOrg) {
      event.preventDefault();
      setShowOrgError(true);
      document.getElementById("selected_org")?.focus();
    }
  };

  const handleOrgChange = (value: string) => {
    setSelectedOrg(value);

    if (value) {
      setShowOrgError(false);
    }
  };

  return (
    <PageWrapper>
      <LoginCard>
        <LoginHeader
          title={i18n.msgStr("chooseOrg")}
          subtitle={i18n.msgStr("chooseOrgSubtitle")}
        />

        <form
          className="space-y-6"
          method="POST"
          action={url.registrationAction}
          onSubmit={handleSubmit}
        >
          <OrgSelect
            i18n={i18n}
            organizations={sortedOrganizations}
            excludedAliases={EXCLUDED_ORG_ALIASES}
            value={selectedOrg}
            onChange={handleOrgChange}
            hasError={showOrgError}
            errorId={"org-not-selected-error"}
          />

          <SubmitButton
            text={i18n.msgStr("signInWithOrg")}
            data-testid="continue-with-selected-org"
            className="w-full"
          />
        </form>

        {otherSignInOptions.length > 0 && (
          <section
            className="space-y-3 border-t border-border pt-6 mt-8"
            aria-labelledby="other-sign-in-options"
          >
            <h2
              id="other-sign-in-options"
              className="text-sm font-medium text-content-muted"
            >
              {i18n.msgStr("otherSignInOptions")}
            </h2>

            {otherSignInOptions.map((org) => (
              <form
                key={org.alias}
                method="POST"
                action={url.registrationAction}
              >
                <button
                  type="submit"
                  name="selected_org"
                  value={org.alias}
                  className="flex min-h-14 w-full cursor-pointer items-center gap-3 rounded-md border border-border-input bg-surface px-4 py-3 text-left transition-colors hover:border-primary hover:bg-surface-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-focus-offset"
                >
                  {org.logo && (
                    <img
                      src={getLogoUrl(org.logo)}
                      alt=""
                      aria-hidden="true"
                      className="h-7 w-7 shrink-0 object-contain"
                    />
                  )}

                  <span className="min-w-0 flex-1 text-sm font-semibold text-accent">
                    {org.name}
                  </span>
                  <span aria-hidden="true" className="text-xl text-accent">
                    →
                  </span>
                </button>
              </form>
            ))}
          </section>
        )}
      </LoginCard>
    </PageWrapper>
  );
};

export const FlaisOrgSelector = React.memo(FlaisOrgSelectorComponent);

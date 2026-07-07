import React, { useMemo, useState } from "react";
import logo from "../assets/images/novari_logo.png";

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

const FlaisOrgSelectorComponent = ({
  kcContext,
  i18n,
}: FlaisOrgSelectorProps) => {
  const { organizations, url } = kcContext;
  const [selectedOrg, setSelectedOrg] = useState("");
  const [showOrgError, setShowOrgError] = useState(false);

  const sortByName = <T extends { name: string }>(items: T[]) =>
    [...items].sort((a, b) =>
      a.name.localeCompare(b.name, "nb", { sensitivity: "base" }),
    );

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
      <LoginCard logo={logo}>
        <LoginHeader
          title={i18n.msgStr("chooseOrg")}
          subtitle={i18n.msgStr("chooseOrgSubtitle")}
        />

        <form
          className="space-y-5"
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
            text={i18n.msgStr("continue")}
            data-testid="continue-with-selected-org"
          />
        </form>

        {otherSignInOptions.length > 0 && (
          <section className="mt-2" aria-labelledby="other-sign-in-options">
            <h2 id="other-sign-in-options" className="mb-3 text-lg font-bold">
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
                  className="flex w-full items-center gap-4 p-2 hover:cursor-pointer hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
                >
                  {org.logo && (
                    <img
                      src={getLogoUrl(org.logo)}
                      alt=""
                      aria-hidden="true"
                      className="h-10 w-10"
                    />
                  )}

                  <span className="text-lg">{org.name}</span>
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

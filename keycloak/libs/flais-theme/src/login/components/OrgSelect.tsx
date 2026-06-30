import React, { useMemo } from "react";
import { LogoDropdownInput } from "./LogoDropdownInput.tsx";
import { getLogoUrl } from "../utils/logo-url.ts";
import { I18n } from "../i18n.ts";

type Organization = {
  alias: string;
  name: string;
  logo?: string;
};

export interface OrgSelectProps {
  i18n: I18n;
  organizations: Organization[];
  excludedAliases?: string[];
  value: string;
  onChange: (value: string) => void;
  hasError: boolean;
  errorId: string
}

const OrgSelectComponent = ({
  i18n,
  organizations,
  excludedAliases = [],
  value,
  onChange,
  hasError,
  errorId
}: OrgSelectProps) => {
  const options = useMemo(
    () =>
      organizations
        .filter((org) => !excludedAliases.includes(org.alias))
        .map((org) => ({
          id: org.alias,
          label: org.name,
          logosUrl: getLogoUrl(org.logo),
        })),
    [excludedAliases, organizations],
  );

  return (
    <div>
      <label htmlFor="selected_org" className="sr-only">
        {i18n.msgStr("chooseOrg")}
      </label>

      <LogoDropdownInput
        id="selected_org"
        placeholder={i18n.msgStr("chooseOrg")}
        name="selected_org"
        options={options}
        onChange={onChange}
        value={value}
        i18n={i18n}
        hasError={hasError}
        errorId={errorId}
      />
    </div>
  );
};

export const OrgSelect = React.memo(OrgSelectComponent);

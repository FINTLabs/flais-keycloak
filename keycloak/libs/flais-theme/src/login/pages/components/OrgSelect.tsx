import React, { useMemo, useState } from "react";
import { I18n } from "../../i18n.ts";
import { LogoDropdownInput } from "./LogoDropdownInput.tsx";

export interface OrgSelectProps {
  i18n: I18n;
  organizations: {
    alias: string;
    name: string;
    logo?: string;
  }[];
}

const getLogosUrl = (logo?: string) => {
  if (!logo) return undefined;

  if(URL.canParse(logo)) return logo

  const baseUrl = import.meta.env.BASE_URL.replace(/\/$/, "");
  const filename = logo.replace(/^\/+/, "");

  return `${baseUrl}/${filename}`;
};

const OrgSelectComponent = ({ i18n, organizations }: OrgSelectProps) => {
  const [selectedIdp, setSelectedIdp] = useState<string>("");

  const options = useMemo(
    () =>
      organizations.map((org) => ({
        id: org.alias,
        label: org.name,
        logosUrl: getLogosUrl(org.logo),
      })),
    [organizations],
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
        onChange={setSelectedIdp}
        value={selectedIdp}
      />
    </div>
  );
};

export const OrgSelect = React.memo(OrgSelectComponent);

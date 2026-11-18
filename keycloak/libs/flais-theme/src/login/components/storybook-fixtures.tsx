import type { Decorator } from "@storybook/react-vite";
import { getKcContextMock } from "../KcPageStory";
import { useI18n } from "../i18n";
import "../main.css";

export const componentFrame: Decorator = (Story) => (
  <div className="min-h-dvh bg-page p-6 text-content">
    <div className="w-[min(28rem,calc(100vw-3rem))]">
      <Story />
    </div>
  </div>
);

export function useExampleI18n() {
  return useI18n({
    kcContext: getKcContextMock({
      pageId: "flais-org-selector.ftl",
      overrides: {},
    }),
  }).i18n;
}

// A generic white mark to check contrast; this is not an organization's crest.
export const whiteMark = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><path fill="white" d="M16 2 29 8v10c0 6-8 10-13 12C11 28 3 24 3 18V8z"/></svg>')}`;

export const sampleOrganizations = [
  { alias: "telemark", name: "Telemark" },
  { alias: "vestland", name: "Vestland" },
  {
    alias: "white-mark",
    name: "Organization with a white logo",
    logo: whiteMark,
  },
  { alias: "id-porten", name: "ID-Porten" },
];

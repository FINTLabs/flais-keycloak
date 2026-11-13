import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fireEvent, fn, userEvent, waitFor } from "storybook/test";
import { LogoDropdownInput, type LogoOption } from "./LogoDropdownInput";
import {
  componentFrame,
  sampleOrganizations,
  useExampleI18n,
} from "./storybook-fixtures";

type ExampleProps = {
  options: LogoOption[];
  hasError: boolean;
  initialValue?: string;
  onChange: (value: string) => void;
};
function SearchableLogoField({
  options,
  hasError,
  onChange,
  initialValue = "",
}: ExampleProps) {
  const i18n = useExampleI18n();
  const [value, setValue] = useState(initialValue);
  return (
    <div className="min-h-80">
      <label htmlFor="affiliation" className="mb-2 block text-sm font-medium">
        Affiliation
      </label>
      <LogoDropdownInput
        id="affiliation"
        name="affiliation"
        options={options}
        value={value}
        onChange={(next) => {
          setValue(next);
          onChange(next);
        }}
        placeholder={i18n.msgStr("searchOrgPlaceholder")}
        i18n={i18n}
        hasError={hasError}
        errorId="affiliation-error"
      />
    </div>
  );
}
const meta = {
  title: "Common/LogoDropdownInput",
  component: SearchableLogoField,
  subcomponents: { LogoDropdownInput },
  decorators: [componentFrame],
  args: {
    options: sampleOrganizations.map((org) => ({
      id: org.alias,
      label: org.name,
      logosUrl: org.logo,
    })),
    hasError: false,
    onChange: fn(),
  },
} satisfies Meta<typeof SearchableLogoField>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const KeyboardSelection: Story = {
  play: async ({ canvas, args }) => {
    const search = canvas.getByRole("combobox");
    await userEvent.click(search);
    fireEvent.change(search, { target: { value: "Vestland" } });
    await waitFor(() => expect(canvas.getAllByRole("option")).toHaveLength(1));
    await userEvent.keyboard("{ArrowDown}{Enter}");
    await expect(search).toHaveValue("Vestland");
    await expect(args.onChange).toHaveBeenLastCalledWith("vestland");
    await expect(search).toHaveAttribute("aria-expanded", "false");
  },
};
export const NoMatches: Story = {
  play: async ({ canvas }) => {
    const search = canvas.getByRole("combobox");
    await userEvent.click(search);
    fireEvent.change(search, { target: { value: "No matching organization" } });
    await waitFor(() =>
      expect(canvas.queryAllByRole("option")).toHaveLength(0),
    );
    await expect(canvas.getByRole("listbox")).toHaveTextContent("No result");
  },
};
export const Required: Story = { args: { hasError: true } };
export const WhiteLogo: Story = { args: { initialValue: "white-mark" } };

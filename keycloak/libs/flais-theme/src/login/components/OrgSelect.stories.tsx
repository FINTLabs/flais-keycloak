import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fireEvent, fn, userEvent, waitFor } from "storybook/test";
import { OrgSelect, type OrgSelectProps } from "./OrgSelect";
import {
  componentFrame,
  sampleOrganizations,
  useExampleI18n,
} from "./storybook-fixtures";

type ExampleProps = Pick<
  OrgSelectProps,
  "organizations" | "hasError" | "onChange"
> & { initialValue?: string };
function OrganizationField({
  organizations,
  hasError,
  onChange,
  initialValue = "",
}: ExampleProps) {
  const i18n = useExampleI18n();
  const [value, setValue] = useState(initialValue);
  return (
    <OrgSelect
      i18n={i18n}
      organizations={organizations}
      excludedAliases={["id-porten"]}
      value={value}
      onChange={(next) => {
        setValue(next);
        onChange(next);
      }}
      hasError={hasError}
      errorId="organization-error"
    />
  );
}
const meta = {
  title: "Common/OrgSelect",
  component: OrganizationField,
  subcomponents: { OrgSelect },
  decorators: [componentFrame],
  args: { organizations: sampleOrganizations, hasError: false, onChange: fn() },
} satisfies Meta<typeof OrganizationField>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
  play: async ({ canvas, args }) => {
    const search = canvas.getByRole("combobox");
    await userEvent.click(search);
    await expect(
      canvas.queryByRole("option", { name: "ID-Porten" }),
    ).not.toBeInTheDocument();
    fireEvent.change(search, { target: { value: "Telemark" } });
    await waitFor(() => expect(canvas.getAllByRole("option")).toHaveLength(1));
    await userEvent.keyboard("{ArrowDown}{Enter}");
    await expect(search).toHaveValue("Telemark");
    await expect(args.onChange).toHaveBeenLastCalledWith("telemark");
  },
};
export const Required: Story = { args: { hasError: true } };
export const NoOrganizations: Story = { args: { organizations: [] } };
export const WhiteLogo: Story = { args: { initialValue: "white-mark" } };

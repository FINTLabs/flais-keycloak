import type { Meta, StoryObj } from "@storybook/react-vite";
import { LoginHeader } from "./LoginHeader";
import { componentFrame } from "./storybook-fixtures";

const meta = {
  title: "Common/LoginHeader",
  component: LoginHeader,
  decorators: [componentFrame],
  args: {
    title: "Choose affiliation",
    subtitle:
      "Search for your organization or choose it from the list to sign in.",
  },
} satisfies Meta<typeof LoginHeader>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const WithoutSubtitle: Story = { args: { subtitle: undefined } };
export const LongTitle: Story = {
  args: { title: "Choose how to sign in to your organization" },
};

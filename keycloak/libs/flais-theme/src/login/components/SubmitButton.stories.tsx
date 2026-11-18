import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import { SubmitButton } from "./SubmitButton";
import { componentFrame } from "./storybook-fixtures";

const meta = {
  title: "Common/SubmitButton",
  component: SubmitButton,
  decorators: [componentFrame],
  args: { text: "Sign in", type: "button", onClick: fn() },
} satisfies Meta<typeof SubmitButton>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
  play: async ({ canvas, args }) => {
    await userEvent.click(canvas.getByRole("button", { name: args.text }));
    await expect(args.onClick).toHaveBeenCalledOnce();
  },
};
export const Disabled: Story = {
  args: { disabled: true },
  play: async ({ canvas, args }) => {
    const button = canvas.getByRole("button", { name: args.text });
    await expect(button).toBeDisabled();
    await userEvent.click(button);
    await expect(args.onClick).not.toHaveBeenCalled();
  },
};

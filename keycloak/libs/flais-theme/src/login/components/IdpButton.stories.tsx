import type { FormEvent } from "react";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";
import { IdpButton } from "./IdpButton";
import { componentFrame } from "./storybook-fixtures";

const onSubmit = fn((event: FormEvent<HTMLFormElement>) =>
  event.preventDefault(),
);
const meta = {
  title: "Common/IdpButton",
  component: IdpButton,
  decorators: [componentFrame],
  args: { alias: "example-provider", name: "Sign in with your work account" },
  beforeEach: () => {
    onSubmit.mockClear();
  },
  render: (args) => (
    <form onSubmit={onSubmit}>
      <IdpButton {...args} />
    </form>
  ),
} satisfies Meta<typeof IdpButton>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
  play: async ({ canvas, args }) => {
    await userEvent.click(canvas.getByRole("button", { name: args.name }));
    await expect(onSubmit).toHaveBeenCalledOnce();
    const event = onSubmit.mock.calls[0][0].nativeEvent as SubmitEvent;
    await expect((event.submitter as HTMLButtonElement).value).toBe(args.alias);
  },
};
export const LongName: Story = {
  args: {
    name: "Sign in with the identity provider managed by your organization",
  },
};

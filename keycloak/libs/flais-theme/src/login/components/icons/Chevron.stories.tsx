import type { Meta, StoryObj } from "@storybook/react-vite";
import { Chevron } from "./Chevron";
import "../../main.css";

const meta = {
  title: "Common/Icons/Chevron",
  component: Chevron,
  parameters: { layout: "centered" },
  args: {
    direction: "down",
    className: "h-5 w-5 text-accent",
    "aria-hidden": true,
  },
} satisfies Meta<typeof Chevron>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Down: Story = {};
export const Up: Story = { args: { direction: "up" } };

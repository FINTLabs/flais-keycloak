import type { Meta, StoryObj } from "@storybook/react-vite";
import { Logo } from "./Logo";
import { componentFrame } from "./storybook-fixtures";
import logo from "../assets/images/novari_logo.png";

const meta = {
  title: "Common/Logo",
  component: Logo,
  decorators: [componentFrame],
  args: { src: logo, alt: "Novari IKS", className: "h-auto w-52" },
} satisfies Meta<typeof Logo>;
export default meta;
type Story = StoryObj<typeof meta>;
export const OnBeige: Story = {};
export const OnBurgundy: Story = {
  decorators: [
    (Story) => (
      <div className="bg-primary p-6">
        <Story />
      </div>
    ),
  ],
};

import type { Meta, StoryObj } from "@storybook/react-vite";
import { PageWrapper } from "./PageWrapper";
import { LoginCard } from "./LoginCard";
import { LoginHeader } from "./LoginHeader";
import "../main.css";

const meta = {
  title: "Common/PageWrapper",
  component: PageWrapper,
  parameters: { layout: "fullscreen" },
  args: {
    children: (
      <LoginCard>
        <LoginHeader
          title="A consistent starting position"
          subtitle="The layout uses the same card width and top spacing across all authentication screens."
        />
      </LoginCard>
    ),
  },
} satisfies Meta<typeof PageWrapper>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const TallContent: Story = {
  args: {
    children: (
      <LoginCard>
        <LoginHeader title="Content can grow" />
        {Array.from({ length: 8 }, (_, index) => (
          <p key={index} className="leading-relaxed text-content-muted">
            Longer content stays accessible by scrolling, while the card retains
            its alignment and comfortable page margins.
          </p>
        ))}
      </LoginCard>
    ),
  },
};

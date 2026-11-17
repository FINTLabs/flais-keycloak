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
          title="Centered on your screen"
          subtitle="The card stays centered horizontally and vertically, with space to scroll when the screen is shorter than the content."
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

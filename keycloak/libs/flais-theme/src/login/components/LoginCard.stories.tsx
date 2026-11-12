import type { Meta, StoryObj } from "@storybook/react-vite";
import { LoginCard } from "./LoginCard";
import { LoginHeader } from "./LoginHeader";
import { PageWrapper } from "./PageWrapper";
import { SubmitButton } from "./SubmitButton";
import logo from "../assets/images/novari_logo.png";
import "../main.css";

const meta = {
  title: "Common/LoginCard",
  component: LoginCard,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <PageWrapper>
        <Story />
      </PageWrapper>
    ),
  ],
  args: {
    logo,
    children: (
      <>
        <LoginHeader
          title="Choose affiliation"
          subtitle="Search for your organization or choose it from the list to sign in."
        />
        <SubmitButton text="Sign in" type="button" />
      </>
    ),
  },
} satisfies Meta<typeof LoginCard>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {};
export const LongContent: Story = {
  args: {
    children: (
      <>
        <LoginHeader
          title="Choose how to sign in to your organization"
          subtitle="The shared card keeps the same horizontal alignment and spacing when content wraps onto several lines."
        />
        <p className="leading-relaxed text-content-muted">
          Your organization manages access to its services. Choose your
          affiliation and sign in with the method provided by your organization.
        </p>
        <SubmitButton text="Sign in" type="button" />
      </>
    ),
  },
};

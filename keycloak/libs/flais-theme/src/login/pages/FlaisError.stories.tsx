import type { Meta, StoryObj } from "@storybook/react-vite";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "error.ftl" });

const meta = {
  title: "login/error.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <KcPageStory />,
};

export const LongErrorMessage: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        message: {
          summary:
            "Your sign-in session has expired. Return to the application and try signing in again. If the problem continues, contact your organization’s support team and include this reference: SIGN_IN_SESSION_EXPIRED_012345678901234567890123456789012345678901234567890123456789.",
        },
      }}
    />
  ),
};

export const WithoutClient: Story = {
  render: () => <KcPageStory kcContext={{ client: undefined }} />,
};

export const WithoutApplicationUrl: Story = {
  render: () => <KcPageStory kcContext={{ client: { baseUrl: "" } }} />,
};

export const WithSkipLink: Story = {
  render: () => <KcPageStory kcContext={{ skipLink: true }} />,
};

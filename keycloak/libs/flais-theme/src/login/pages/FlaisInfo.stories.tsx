import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "info.ftl" });

const meta = {
  title: "login/info.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <KcPageStory />,
  play: async ({ canvas }) => {
    await expect(
      await canvas.findByRole("heading", {
        level: 1,
        name: "Request completed",
      }),
    ).toBeVisible();
  },
};

export const WithoutMessageHeader: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        messageHeader: undefined,
        message: { summary: "Your email address has been verified." },
      }}
    />
  ),
  play: async ({ canvas }) => {
    await expect(
      await canvas.findByRole("heading", {
        level: 1,
        name: "Your email address has been verified.",
      }),
    ).toBeVisible();
  },
};

export const LogoutSuccess: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        messageHeader: "successLogout",
        message: { summary: "Your session has ended." },
        actionUri: undefined,
      }}
    />
  ),
  play: async ({ canvas }) => {
    await expect(
      await canvas.findByRole("heading", {
        level: 1,
        name: "You are logged out",
      }),
    ).toBeVisible();
  },
};

export const WithoutApplicationUrl: Story = {
  render: () => <KcPageStory kcContext={{ client: { baseUrl: "" } }} />,
};

export const WithSkipLink: Story = {
  render: () => <KcPageStory kcContext={{ skipLink: true }} />,
};

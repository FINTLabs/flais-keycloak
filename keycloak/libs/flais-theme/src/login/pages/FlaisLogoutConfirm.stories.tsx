import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "logout-confirm.ftl" });

const meta = {
  title: "login/logout-confirm.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <KcPageStory />,
};

export const WithoutApplicationUrl: Story = {
  render: () => <KcPageStory kcContext={{ client: { baseUrl: "" } }} />,
};

export const WithSkipLink: Story = {
  render: () => (
    <KcPageStory kcContext={{ logoutConfirm: { skipLink: true } }} />
  ),
};

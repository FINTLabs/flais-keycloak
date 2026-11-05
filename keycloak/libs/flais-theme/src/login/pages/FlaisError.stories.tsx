import type { Meta, StoryObj } from "@storybook/react";
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

export const WithoutClient: Story = {
  render: () => <KcPageStory kcContext={{ client: undefined }} />,
};

export const WithoutApplicationUrl: Story = {
  render: () => <KcPageStory kcContext={{ client: { baseUrl: "" } }} />,
};

export const WithSkipLink: Story = {
  render: () => <KcPageStory kcContext={{ skipLink: true }} />,
};

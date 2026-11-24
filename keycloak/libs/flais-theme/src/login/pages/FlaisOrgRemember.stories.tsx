import type { Meta, StoryObj } from "@storybook/react-vite";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "flais-org-remember.ftl" });

const meta = {
  title: "login/flais-org-remember.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <KcPageStory />,
};

export const Norwegian: Story = {
  render: () => (
    <KcPageStory kcContext={{ locale: { currentLanguageTag: "no" } }} />
  ),
};

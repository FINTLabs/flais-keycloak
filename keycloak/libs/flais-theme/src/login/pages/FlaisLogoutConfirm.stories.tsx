import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
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
  play: async ({ canvas }) => {
    await expect(
      canvas.getByText("Are you sure you want to sign out?"),
    ).toBeVisible();
    await expect(
      canvas.getByRole("link", { name: "No, back to application" }),
    ).toHaveAttribute("href");
    const confirm = canvas.getByRole("button", { name: "Yes" });
    const form = confirm.closest("form")!;
    const onSubmit = fn((event: Event) => event.preventDefault());
    form.addEventListener("submit", onSubmit);
    try {
      confirm.click();
      await expect(onSubmit).toHaveBeenCalledOnce();
      await expect(confirm).toHaveAttribute("name", "confirmLogout");
      await expect(form).toHaveAttribute("method", "POST");
    } finally {
      form.removeEventListener("submit", onSubmit);
    }
  },
};

export const WithoutApplicationUrl: Story = {
  render: () => <KcPageStory kcContext={{ client: { baseUrl: "" } }} />,
  play: async ({ canvas }) => {
    await expect(
      canvas.queryByRole("link", { name: "No, back to application" }),
    ).not.toBeInTheDocument();
    await expect(canvas.getByRole("button", { name: "Yes" })).toBeVisible();
  },
};

export const WithSkipLink: Story = {
  render: () => (
    <KcPageStory kcContext={{ logoutConfirm: { skipLink: true } }} />
  ),
  play: WithoutApplicationUrl.play,
};

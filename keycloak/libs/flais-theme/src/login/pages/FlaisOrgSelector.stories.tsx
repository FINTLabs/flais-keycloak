import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fireEvent, userEvent, waitFor } from "storybook/test";
import { createKcPageStory, mockOrganizations } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "flais-org-selector.ftl" });

const meta = {
  title: "login/flais-org-selector.ftl",
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => <KcPageStory />,
};

export const SearchAndKeyboardSelection: Story = {
  render: () => <KcPageStory />,
  play: async ({ canvas }) => {
    const search = await canvas.findByRole("combobox");
    await userEvent.click(search);
    fireEvent.change(search, { target: { value: "Telemark" } });
    await waitFor(() => expect(canvas.getAllByRole("option")).toHaveLength(1));
    await userEvent.keyboard("{ArrowDown}{Enter}");
    await expect(search).toHaveValue("Telemark");
    await expect(search).toHaveAttribute("aria-expanded", "false");
  },
};

export const RequiredAffiliation: Story = {
  render: () => <KcPageStory />,
  play: async ({ canvas }) => {
    const search = await canvas.findByRole("combobox");
    await userEvent.click(canvas.getByTestId("continue-with-selected-org"));
    await expect(canvas.getByRole("alert")).toBeVisible();
    await expect(search).toHaveAttribute("aria-invalid", "true");
    await expect(search).toHaveFocus();
    fireEvent.change(search, { target: { value: "Telemark" } });
    await waitFor(() => expect(canvas.getAllByRole("option")).toHaveLength(1));
    await userEvent.keyboard("{ArrowDown}{Enter}");
    await expect(search).toHaveAttribute("aria-invalid", "false");
    await expect(canvas.queryByRole("alert")).not.toBeInTheDocument();
  },
};

export const SingleOrganization: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: mockOrganizations.slice(0, 1),
      }}
    />
  ),
};

export const TwoOrganizations: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: mockOrganizations.slice(0, 2),
      }}
    />
  ),
};

export const ManyOrganizations: Story = {
  render: () => <KcPageStory />,
};

export const NoOrganizations: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: [],
      }}
    />
  ),
};

export const MissingLogos: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: mockOrganizations.slice(0, 3).map((organization) => ({
          ...organization,
          logo: undefined,
        })),
      }}
    />
  ),
};

export const BrokenLogoUrl: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: [
          {
            ...mockOrganizations[0],
            logo: "https://example.com/does-not-exist.svg",
          },
          mockOrganizations[1],
        ],
      }}
    />
  ),
};

export const LongOrganizationNames: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: [
          {
            ...mockOrganizations[0],
            alias: "very-long-name",
            name: "This Is A Very Long Organization Name That Should Truncate Correctly",
          },
          {
            ...mockOrganizations.find(
              (organization) => organization.alias === "more-og-romsdal",
            )!,
            name: "Møre og Romsdal county with extra long name to truncate",
          },
        ],
      }}
    />
  ),
};

export const WithoutIdPorten: Story = {
  render: () => (
    <KcPageStory
      kcContext={{
        organizations: mockOrganizations.filter(
          (organization) => organization.alias !== "id-porten",
        ),
      }}
    />
  ),
};

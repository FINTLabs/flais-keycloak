import type { Meta, StoryObj } from '@storybook/react'
import { createKcPageStory } from '../KcPageStory'

const { KcPageStory } = createKcPageStory({
  pageId: 'flais-org-idp-selector.ftl',
})

const meta = {
  title: 'login/flais-org-idp-selector.ftl',
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>

export default meta

type Story = StoryObj<typeof meta>

export const Default: Story = {
  render: () => <KcPageStory />,
}

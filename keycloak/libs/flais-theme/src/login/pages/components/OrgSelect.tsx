import React, { useMemo, useState } from 'react'
import { I18n } from '../../i18n.ts'
import { ImageDropdownInput } from './ImageDropdownInput.tsx'

export interface OrgSelectProps {
  i18n: I18n
  organizations: {
    alias: string
    name: string
    logo?: string
  }[]
}

const OrgSelectComponent = ({ i18n, organizations }: OrgSelectProps) => {
  const [selectedIdp, setSelectedIdp] = useState<string>('')

  const options = useMemo(
    () =>
      organizations.map((org) => ({
        id: org.alias,
        label: org.name,
        imageUrl: org.logo,
      })),
    [organizations]
  )

  return (
    <div>
      <label htmlFor="org" className="sr-only">
        {i18n.msgStr('chooseOrg')}
      </label>

      <ImageDropdownInput
        placeholder={i18n.msgStr('chooseOrg')}
        name="selected_org"
        options={options}
        onChange={setSelectedIdp}
        value={selectedIdp}
      />
    </div>
  )
}

export const OrgSelect = React.memo(OrgSelectComponent)

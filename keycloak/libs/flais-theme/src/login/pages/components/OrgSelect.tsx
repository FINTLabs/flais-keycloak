import ImageDropdownInput from './ImageDropdownInput.tsx'
import { useMemo, useState } from 'react'

type OrgSelectProps = {
  organizations: {
    alias: string
    name: string
    logo?: string
  }[]
}

const OrgSelect = ({ organizations }: OrgSelectProps) => {
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
        Velg tilhørighet
      </label>

      <ImageDropdownInput
        name="selected_org"
        options={options}
        onChange={setSelectedIdp}
        value={selectedIdp}
      />
    </div>
  )
}

export default OrgSelect

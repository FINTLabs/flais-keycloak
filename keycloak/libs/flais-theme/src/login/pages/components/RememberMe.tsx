import React from 'react'
import { I18n } from '../../i18n'

export interface RememberMeProps {
  i18n: I18n
}

const RememberMeComponent = ({ i18n }: RememberMeProps) => {
  return (
    <div className="flex items-center">
      <input
        name="remember_me"
        id="remember"
        type="checkbox"
        className="h-4 w-4 text-primary focus:ring-primary rounded"
      />
      <label htmlFor="remember" className="ml-2 text-sm text-accent">
        {i18n.msgStr('rememberMe')}
      </label>
    </div>
  )
}

export const RememberMe = React.memo(RememberMeComponent)

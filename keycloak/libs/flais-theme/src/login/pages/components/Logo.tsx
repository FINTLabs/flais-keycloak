import React, { InputHTMLAttributes } from 'react'

export interface LogoProps extends InputHTMLAttributes<HTMLImageElement> {}

const LogoComponent = (props: LogoProps) => {
  return <img alt="Novari logo" {...props} />
}

export const Logo = React.memo(LogoComponent)

/* eslint-disable react/prop-types */
import React, { InputHTMLAttributes } from 'react'

type LogoProps = InputHTMLAttributes<HTMLImageElement>

export const Logo: React.FC<LogoProps> = ({ ...props }) => (
  <img alt="Novari logo" {...props} />
)

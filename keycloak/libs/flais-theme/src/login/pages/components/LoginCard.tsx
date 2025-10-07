import React, { ReactNode } from 'react'

export interface LoginCardProps {
  logo: string
  children: ReactNode
}

const LoginCardComponent = ({ logo, children }: LoginCardProps) => {
  return (
    <div className="w-full max-w-md">
      <div className="flex justify-center mb-2">
        <img src={logo} alt="Logo" className="h-22 w-auto" />
      </div>

      <div className="relative bg-white shadow-lg border border-gray-100 overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-1 bg-primary" />
        <div className="pt-6 px-8 pb-8 space-y-6">{children}</div>
      </div>
    </div>
  )
}

export const LoginCard = React.memo(LoginCardComponent)

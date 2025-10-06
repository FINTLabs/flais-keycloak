import React, { ReactNode } from 'react'

export interface PageWrapperProps {
  children: ReactNode
}

const PageWrapperComponent = ({ children }: PageWrapperProps) => {
  return (
    <div className="min-h-screen flex items-center justify-center bg-cream px-4">
      {children}
    </div>
  )
}

export const PageWrapper = React.memo(PageWrapperComponent)

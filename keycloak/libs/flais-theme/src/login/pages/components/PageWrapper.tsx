import { ReactNode } from 'react'

interface PageWrapperProps {
  children: ReactNode
}

const PageWrapper = ({ children }: PageWrapperProps) => {
  return (
    <div className="min-h-screen flex items-center justify-center bg-cream px-4">
      {children}
    </div>
  )
}

export default PageWrapper

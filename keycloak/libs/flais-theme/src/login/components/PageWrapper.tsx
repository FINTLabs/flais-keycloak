import React, { type ReactNode } from "react";

export interface PageWrapperProps {
  children: ReactNode;
}

const PageWrapperComponent = ({ children }: PageWrapperProps) => {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-cream px-4 py-6 sm:px-6 lg:px-8">
      {children}
    </main>
  );
};

export const PageWrapper = React.memo(PageWrapperComponent);

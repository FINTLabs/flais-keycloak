import React, { type ReactNode } from "react";

export interface PageWrapperProps {
  children: ReactNode;
}

const PageWrapperComponent = ({ children }: PageWrapperProps) => {
  return (
    <main className="flex min-h-screen items-center justify-center bg-cream px-4">
      {children}
    </main>
  );
};

export const PageWrapper = React.memo(PageWrapperComponent);

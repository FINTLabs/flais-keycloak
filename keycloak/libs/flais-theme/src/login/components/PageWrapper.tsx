import React, { type ReactNode } from "react";

export interface PageWrapperProps {
  children: ReactNode;
}

const PageWrapperComponent = ({ children }: PageWrapperProps) => {
  return (
    <main className="login-page flex min-h-svh items-start justify-center bg-page px-4 pb-6 sm:px-6 lg:px-8">
      {children}
    </main>
  );
};

export const PageWrapper = React.memo(PageWrapperComponent);

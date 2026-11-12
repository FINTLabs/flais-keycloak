import React, { type ReactNode } from "react";

export interface PageWrapperProps {
  children: ReactNode;
}

const PageWrapperComponent = ({ children }: PageWrapperProps) => {
  return (
    <main className="flex min-h-dvh items-start justify-center bg-page px-4 pb-12 pt-[clamp(2rem,10vh,6rem)] sm:px-6 lg:px-8">
      {children}
    </main>
  );
};

export const PageWrapper = React.memo(PageWrapperComponent);

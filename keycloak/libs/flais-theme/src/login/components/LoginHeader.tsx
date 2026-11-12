import React, { type ReactNode } from "react";

export interface LoginHeaderProps {
  title: ReactNode;
  subtitle?: string;
}

const LoginHeaderComponent = ({ title, subtitle }: LoginHeaderProps) => {
  return (
    <header className="space-y-2 text-center">
      <h1
        id="login-title"
        className="text-2xl font-semibold text-content sm:text-3xl lg:text-4xl"
      >
        {title}
      </h1>
      {subtitle && (
        <p className="text-sm text-content-muted sm:text-base">{subtitle}</p>
      )}
    </header>
  );
};

export const LoginHeader = React.memo(LoginHeaderComponent);

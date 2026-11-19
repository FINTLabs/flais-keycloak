import React, { type ReactNode } from "react";

export interface LoginHeaderProps {
  title: ReactNode;
  subtitle?: string;
}

const LoginHeaderComponent = ({ title, subtitle }: LoginHeaderProps) => {
  return (
    <header className="space-y-3 text-left">
      <h1
        id="login-title"
        className="text-2xl font-semibold leading-tight text-accent sm:text-3xl"
      >
        {title}
      </h1>
      {subtitle && (
        <p className="text-base leading-relaxed text-content-muted">
          {subtitle}
        </p>
      )}
    </header>
  );
};

export const LoginHeader = React.memo(LoginHeaderComponent);

import React from "react";

export interface LoginHeaderProps {
  title: string;
  subtitle: string;
}

const LoginHeaderComponent = ({ title, subtitle }: LoginHeaderProps) => {
  return (
    <header className="space-y-2 text-center">
      <h1 id="login-title" className="text-3xl font-semibold text-accent">
        {title}
      </h1>
      <p className="text-sm text-gray-700">{subtitle}</p>
    </header>
  );
};

export const LoginHeader = React.memo(LoginHeaderComponent);

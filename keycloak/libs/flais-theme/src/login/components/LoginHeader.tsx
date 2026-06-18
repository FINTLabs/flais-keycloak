import React from "react";

export interface LoginHeaderProps {
  title: string;
  subtitle: string;
}

const LoginHeaderComponent = ({ title, subtitle }: LoginHeaderProps) => {
  return (
    <header className="space-y-2 text-center">
      <h1
        id="login-title"
        className="text-2xl font-semibold text-accent sm:text-3xl lg:text-4xl"
      >
        {title}
      </h1>
      <p className="text-sm text-gray-700 sm:text-base">{subtitle}</p>
    </header>
  );
};

export const LoginHeader = React.memo(LoginHeaderComponent);

import React, { type ReactNode } from "react";

export interface LoginCardProps {
  logo: string;
  children: ReactNode;
}

const LoginCardComponent = ({ logo, children }: LoginCardProps) => {
  return (
    <section
      className="w-full max-w-xl"
      role="region"
      aria-labelledby="login-title"
    >
      <div className="mb-5 px-6 sm:px-10">
        <img src={logo} alt="Novari IKS" className="-ml-8 h-auto w-52" />
      </div>

      <div className="relative rounded-lg border border-border-subtle bg-surface shadow-sm shadow-shadow">
        <div className="space-y-8 px-6 py-8 sm:px-10 sm:py-10">{children}</div>
      </div>
    </section>
  );
};

export const LoginCard = React.memo(LoginCardComponent);

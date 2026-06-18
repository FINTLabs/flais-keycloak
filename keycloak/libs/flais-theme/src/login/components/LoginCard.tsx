import React, { type ReactNode } from "react";

export interface LoginCardProps {
  logo: string;
  children: ReactNode;
}

const LoginCardComponent = ({ logo, children }: LoginCardProps) => {
  return (
    <section
      className="w-full max-w-md"
      role="region"
      aria-labelledby="login-title"
    >
      <div className="mb-2 flex justify-center">
        <img src={logo} alt="" className="h-24 w-auto" aria-hidden="true" />
      </div>

      <div className="relative border border-gray-100 bg-white shadow-lg">
        <div className="space-y-6 px-8 pb-8 pt-6">{children}</div>
      </div>
    </section>
  );
};

export const LoginCard = React.memo(LoginCardComponent);

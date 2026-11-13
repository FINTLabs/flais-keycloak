import React, { type ReactNode } from "react";
import logo from "../assets/images/Novari_Logo_RGB.png";

export interface LoginCardProps {
  children: ReactNode;
}

const LoginCardComponent = ({ children }: LoginCardProps) => {
  return (
    <section
      className="w-full max-w-md"
      role="region"
      aria-labelledby="login-title"
    >
      <div className="relative rounded-lg border border-border-subtle bg-surface shadow-sm shadow-shadow">
        <div className="px-6 py-8 sm:px-10 sm:py-10">
          <img src={logo} alt="Novari IKS" className="mb-6 h-auto w-20" />
          <div className="space-y-4">{children}</div>
        </div>
      </div>
    </section>
  );
};

export const LoginCard = React.memo(LoginCardComponent);

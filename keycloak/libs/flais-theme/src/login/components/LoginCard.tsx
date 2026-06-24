import React, { type ReactNode } from "react";

export interface LoginCardProps {
  logo: string;
  children: ReactNode;
}

const LoginCardComponent = ({ logo, children }: LoginCardProps) => {
  return (
    <section
      className="w-full max-w-lg lg:max-w-xl"
      role="region"
      aria-labelledby="login-title"
    >
      <div className="mb-3 flex justify-center sm:mb-4">
        <img
          src={logo}
          alt=""
          className="h-20 w-auto sm:h-24 lg:h-28"
          aria-hidden="true"
        />
      </div>

      <div className="relative border border-gray-100 bg-white shadow-lg">
        <div className="space-y-5 px-5 py-6 sm:space-y-6 sm:px-8 sm:py-8 lg:px-10 lg:py-10">
          {children}
        </div>
      </div>
    </section>
  );
};

export const LoginCard = React.memo(LoginCardComponent);

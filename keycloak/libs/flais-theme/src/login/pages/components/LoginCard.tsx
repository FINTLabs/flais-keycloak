import React, { ReactNode } from "react";

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
            <div className="flex justify-center mb-2">
                <img
                    src={logo}
                    alt=""
                    className="h-24 w-auto"
                    aria-hidden="true"
                />
            </div>

            <div className="relative bg-white shadow-lg border border-gray-100 overflow-hidden">
                <div className="pt-6 px-8 pb-8 space-y-6">{children}</div>
            </div>
        </section>
    );
};

export const LoginCard = React.memo(LoginCardComponent);

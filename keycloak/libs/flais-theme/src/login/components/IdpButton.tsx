import React from "react";

export interface IdpButtonProps {
  alias: string;
  name: string;
}

const IdpButtonComponent = ({ alias, name }: IdpButtonProps) => {
  return (
    <button
      type="submit"
      name="identity_provider"
      value={alias}
      className="
        flex w-full cursor-pointer items-center justify-between rounded-none
        bg-white px-4 py-3
        transition-colors duration-150 ease-in-out
        hover:bg-gray-50
        active:bg-gray-100
        focus:outline-none focus:ring-2 focus:ring-inset focus:ring-primary
      "
    >
      <span className="font-medium text-accent">{name}</span>
      <svg
        aria-hidden="true"
        className="h-5 w-5 shrink-0 text-gray-400"
        fill="none"
        focusable="false"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 5l7 7-7 7"
        />
      </svg>
    </button>
  );
};

export const IdpButton = React.memo(IdpButtonComponent);

import React from "react";

export interface SubmitButtonProps {
  text: string;
  disabled?: boolean;
}

const SubmitButtonComponent = ({ text, disabled = false }: SubmitButtonProps) => {
  return (
    <button
      type="submit"
      disabled={disabled}
      className="
        w-full rounded-lg bg-primary px-4 py-3 text-sm font-medium uppercase text-white
        sm:py-3.5 sm:text-base
        hover:bg-primary/90
        focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2
        disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-primary
      "
    >
      {text}
    </button>
  );
};

export const SubmitButton = React.memo(SubmitButtonComponent);

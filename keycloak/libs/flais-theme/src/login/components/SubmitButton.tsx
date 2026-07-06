import React from "react";

export interface SubmitButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  text: string;
}

const SubmitButtonComponent = ({ text, ...buttonProps }: SubmitButtonProps) => {
  return (
    <button
      type="submit"
      className="
        w-full rounded-lg bg-primary px-4 py-3 text-sm font-medium uppercase text-white
        sm:py-3.5 sm:text-base
        hover:bg-primary/90 hover:cursor-pointer
        focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2
      "
      {...buttonProps}
    >
      {text}
    </button>
  );
};

export const SubmitButton = React.memo(SubmitButtonComponent);

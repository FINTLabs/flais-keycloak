import React from "react";

export interface SubmitButtonProps {
  text: string;
}

const SubmitButtonComponent = ({ text }: SubmitButtonProps) => {
  return (
    <button
      type="submit"
      className="
        w-full rounded-lg bg-primary py-3 font-medium uppercase text-white
        hover:bg-primary/90
        focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2
      "
    >
      {text}
    </button>
  );
};

export const SubmitButton = React.memo(SubmitButtonComponent);

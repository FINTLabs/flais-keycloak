import React from "react";

export interface SubmitButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  text: string;
}

const SubmitButtonComponent = ({ text, ...buttonProps }: SubmitButtonProps) => {
  return (
    <button type="submit" className="login-action" {...buttonProps}>
      {text}
    </button>
  );
};

export const SubmitButton = React.memo(SubmitButtonComponent);

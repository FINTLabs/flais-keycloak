import React from "react";
import {cn} from "../utils/cn.ts";

export interface SubmitButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  text: string;
}

const SubmitButtonComponent = ({ text, className, ...buttonProps }: SubmitButtonProps) => {
  return (
    <button type="submit" className={cn("login-action", className)} {...buttonProps}>
      {text}
    </button>
  );
};

export const SubmitButton = React.memo(SubmitButtonComponent);

import React from "react";

export type ChevronDirection = "up" | "down";

export interface ChevronProps extends React.SVGProps<SVGSVGElement> {
  direction?: ChevronDirection;
}

const rotationByDirection: Record<ChevronDirection, string> = {
  up: "rotate-180",
  down: "rotate-0",
};

const ChevronComponent = ({
  direction = "down",
  className = "",
  ...props
}: ChevronProps) => {
  return (
    <svg
      className={`self-center ${rotationByDirection[direction]} ${className}`}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 20 20"
      fill="currentColor"
      focusable="false"
      {...props}
    >
      <path
        fillRule="evenodd"
        d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
        clipRule="evenodd"
      />
    </svg>
  );
};

export const Chevron = React.memo(ChevronComponent);

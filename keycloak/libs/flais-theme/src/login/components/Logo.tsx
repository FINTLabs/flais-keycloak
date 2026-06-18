import React, { type ImgHTMLAttributes } from "react";

export type LogoProps = ImgHTMLAttributes<HTMLImageElement>;

const LogoComponent = ({ alt = "Novari logo", ...props }: LogoProps) => {
  return <img alt={alt} {...props} />;
};

export const Logo = React.memo(LogoComponent);

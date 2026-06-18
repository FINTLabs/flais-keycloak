export const getLogosUrl = (logo?: string) => {
  if (!logo) return undefined;

  if (URL.canParse(logo)) return logo;

  const baseUrl = import.meta.env.BASE_URL.replace(/\/$/, "");
  const filename = logo.replace(/^\/+/, "");

  return `${baseUrl}/${filename}`;
};

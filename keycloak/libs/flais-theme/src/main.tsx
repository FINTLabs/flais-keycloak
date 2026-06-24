import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./kc.gen";

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("Unable to mount app: #root element was not found.");
}

createRoot(rootElement).render(
  <StrictMode>
    {window.kcContext ? (
      <KcPage kcContext={window.kcContext} />
    ) : (
      <main className="p-6" role="alert">
        <h1>No Keycloak Context</h1>
      </main>
    )}
  </StrictMode>,
);

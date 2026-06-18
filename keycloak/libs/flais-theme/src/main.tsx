import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./kc.gen";

// The following block can be uncommented to test a specific page with `yarn dev`
// Don't forget to comment back or your bundle size will increase
/*
import { getKcContextMock } from "./login/KcPageStory";

if (import.meta.env.DEV) {
    window.kcContext = getKcContextMock({
        pageId: "register.ftl",
        overrides: {}
    });
}
*/

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

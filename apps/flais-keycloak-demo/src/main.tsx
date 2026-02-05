import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import Keycloak from "keycloak-js";

// Initialize Keycloak
const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8890",
    realm: import.meta.env.VITE_REALM ?? "external",
    clientId: import.meta.env.VITE_CLIENT_ID ?? "flais-keycloak-demo",
});

keycloak
    .init({
        onLoad: "check-sso",
    })
    .then((authenticated) => {
        if (authenticated) {
            createRoot(document.getElementById("root")!).render(
                <StrictMode>
                    <App keycloak={keycloak} />
                </StrictMode>,
            );
        } else {
            keycloak.login({ scope: "organization" });
        }
    });

import { Issuer } from "./issuer/issuer";
import express from "express";

const port = 9090;
const publicBase = `http://localhost:${port}`;

const tenantId = "flais-scim-auth";
const audience = "8adf8e6e-67b2-4cf2-a259-e3dc5476c621";

const app = express();
app.use(express.json());

app.get("/healthz", (_req, res) => res.send("ok"));

app.listen(port, () => {
    console.log(`Server is running on port ${port}`);
});

const start = async () => {
    const issuer: Issuer = await new Issuer(app, {
        issuer: `${publicBase}/tenant/${tenantId}/`,
        audience,
        alg: "RS256",
        tokenTtlSec: 3600,
    }).init();
};

(async () => {
    try {
        start();
    } catch (e: Error | any) {
        console.log("Error during startup:", e);
        process.exit(1);
    }
})();

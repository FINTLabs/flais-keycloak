import { Issuer } from "./issuer/issuer";
import logger from "./logger/logger";
import express from "express";
import { Provisioner } from "./provisioner/provisioner";

const port = 9090;
const tenantId =
    process.env.TENANT_ID ?? "11111111-1111-1111-1111-111111111111";
const audience = process.env.AUDIENCE ?? "8adf8e6e-67b2-4cf2-a259-e3dc5476c621";
const publicBase = `http://localhost:${port}`;

const app = express();
app.use(express.json());

app.get("/healthz", (_req, res) => res.send("ok"));

app.listen(port, () => {
    logger.info(`Server is running on port ${port}`);
});

const start = async () => {
    const provisioner: Provisioner = new Provisioner(app, publicBase);
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
        logger.error("Error during startup:", e);
        process.exit(1);
    }
})();

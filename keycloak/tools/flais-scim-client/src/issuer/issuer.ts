import logger from "../logger/logger";
import { makeKeys, signToken } from "./keys";
import { Express, Request, Response } from "express";

export interface IssuerOptions {
    issuer: string;
    audience: string;
    alg: "RS256";
    tokenTtlSec: number;
}

export class Issuer {
    private privateKey!: CryptoKey;
    private jwk!: JsonWebKey;

    constructor(app: Express, public opts: IssuerOptions) {
        app.get("/discovery/v2.0/keys", (_req, res) => this.keys(res));
        app.post("/token", async (req, res) => {
            try {
                this.token(req, res);
            } catch (e: any) {
                res.status(500).json({ error: e.message });
            }
        });

        logger.info(`JWKS: /discovery/v2.0/keys`);
        logger.info(`Token: POST /token`);
    }

    async init() {
        const { privateKey, jwk } = await makeKeys(this.opts.alg);
        this.privateKey = privateKey;
        this.jwk = jwk;
        return this;
    }

    async keys(res: Response) {
        res.json({ keys: [this.jwk] });
    }

    async token(req: Request, res: Response) {
        const oid = req.body?.oid || crypto.randomUUID();
        const aud = req.body?.aud || this.opts.audience;
        const iss = req.body?.iss || this.opts.issuer;
        const ttl = Number(req.body?.ttlSec || this.opts.tokenTtlSec);
        const sub = req.body?.sub || oid;

        const jwt = await signToken({
            privateKey: this.privateKey,
            alg: this.opts.alg,
            issuer: iss,
            audience: aud,
            subject: sub,
            oid,
            ttlSec: ttl,
        });

        res.json({
            access_token: jwt,
            token_type: "Bearer",
            expires_in: ttl,
            oid,
            iss,
            aud,
        });
    }
}

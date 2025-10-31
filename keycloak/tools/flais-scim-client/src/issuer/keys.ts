import { generateKeyPair, exportJWK, SignJWT, JWK } from "jose";
import { nanoid } from "nanoid";

export const makeKeys = async (alg: "RS256") => {
    const { publicKey, privateKey } = await generateKeyPair(alg);
    const jwk = await exportJWK(publicKey);
    (jwk as any).kid = nanoid(10);
    (jwk as any).alg = alg;
    return { privateKey, jwk: jwk as JWK };
};

export const signToken = async (options: {
    privateKey: CryptoKey;
    alg: "RS256";
    issuer: string;
    audience: string;
    subject: string;
    oid: string;
    ttlSec: number;
}) => {
    const now = Math.floor(Date.now() / 1000);
    const jti = crypto.randomUUID();

    return await new SignJWT({ oid: options.oid })
        .setProtectedHeader({ alg: options.alg })
        .setIssuer(options.issuer)
        .setAudience(options.audience)
        .setSubject(options.subject || options.oid || "subject")
        .setIssuedAt(now)
        .setExpirationTime(now + options.ttlSec)
        .setJti(jti)
        .sign(options.privateKey);
};

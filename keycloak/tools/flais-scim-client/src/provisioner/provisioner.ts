import { toScimUserDoc, findUser, createUser, updateUser } from "./scim";
import { createHttpclient } from "../network/scim-client";
import logger from "../logger/logger";
import { Express, Request, Response } from "express";

type UserInput = {
    externalId: string;
    userName: string;
    active: boolean;
    name: { givenName: string; familyName: string };
    emails: Array<{ value: string; primary?: boolean; type?: string }>;
    [k: string]: unknown;
};

type ProvisionResult = {
    total: number;
    ok: number;
    details: Array<{
        index: number;
        userName?: string;
        error?: string;
    }>;
};

export class Provisioner {
    constructor(public app: Express, public issuerUrl: string) {
        this.app.post(
            `/provision/:orgId`,
            async (req: Request, res: Response) => {
                const { orgId } = req.params;

                if (!/^[a-zA-Z0-9_-]+$/.test(orgId)) {
                    return res.status(400).json({
                        message: "Invalid orgId.",
                    });
                }

                try {
                    if (!Array.isArray(req.body)) {
                        return res.status(400).json({
                            message: "Body must be an array of user objects.",
                        });
                    }

                    const users = req.body as UserInput[];

                    const result = await this.provision(orgId, users);
                    res.status(200).json({
                        message: "provisioning completed",
                        result,
                    });
                } catch (e: any) {
                    logger.error(`Provisioning error: ${e?.message || e}`);
                    res.status(500).json({
                        message: "provisioning failed",
                        error: e?.message || String(e),
                    });
                }
            }
        );
    }

    async provision(
        orgId: string,
        users: UserInput[]
    ): Promise<ProvisionResult> {
        const baseUrl =
            process.env.KEYCLOAK_BASE_URL ?? "http://localhost:8890";

        const result: ProvisionResult = {
            total: users.length,
            ok: 0,
            details: [],
        };

        const http = await createHttpclient(this.issuerUrl, baseUrl);

        for (let i = 0; i < users.length; i++) {
            const u = users[i] || {};
            const per = {
                index: i + 1,
                userName: u.userName,
            };

            const desired = toScimUserDoc(u);
            const existing = await findUser(http, u.userName, orgId);
            let userId: string | undefined;

            if (existing) {
                userId = existing.id;
                await updateUser(http, userId!, desired, orgId);
                logger.info(`[${i + 1}] UPDATE ${u.userName} (id=${userId})`);
            } else {
                const created = await createUser(http, desired, orgId);
                userId = created.id;
                logger.info(`[${i + 1}] CREATE ${u.userName} (id=${userId})`);
            }

            result.ok++;
            result.details.push(per);
        }

        logger.info(`Provisioning result: ${JSON.stringify(result, null, 2)}`);
        return result;
    }
}

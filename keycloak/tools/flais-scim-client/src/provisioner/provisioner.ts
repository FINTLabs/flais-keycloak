import {
    toScimUserDoc,
    findUser,
    createUser,
    updateUser,
    deleteUser,
} from "./scim";
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
        action?: "create" | "update" | "delete" | "delete-skip";
        error?: string;
    }>;
};

export class Provisioner {
    constructor(public app: Express, public issuerUrl: string) {
        this.app.post(`/provision/:orgId`, this.handleProvision);
        this.app.post(`/deprovision/:orgId`, this.handleDeprovision);
    }

    private handleDeprovision = async (req: Request, res: Response) => {
        const { orgId } = req.params;
        if (!/^[a-zA-Z0-9_-]+$/.test(orgId)) {
            return res.status(400).json({ message: "Invalid orgId." });
        }

        try {
            if (!Array.isArray(req.body)) {
                return res.status(400).json({
                    message: "Body must be an array of { userName }.",
                });
            }

            const items = req.body as Array<{ userName: string }>;
            const result = await this.deprovision(
                orgId,
                items.map((x) => x.userName)
            );

            res.status(200).json({
                message: "deprovisioning completed",
                result,
            });
        } catch (e: any) {
            logger.error(`Deprovisioning error: ${e?.message || e}`);
            res.status(500).json({
                message: "deprovisioning failed",
                error: e?.message || String(e),
            });
        }
    };

    private handleProvision = async (req: Request, res: Response) => {
        const { orgId } = req.params;
        if (!/^[a-zA-Z0-9_-]+$/.test(orgId)) {
            return res.status(400).json({ message: "Invalid orgId." });
        }

        try {
            if (!Array.isArray(req.body)) {
                return res.status(400).json({
                    message: "Body must be an array of user objects.",
                });
            }

            const users = req.body as UserInput[];
            const result = await this.provision(orgId, users);

            res.status(200).json({ message: "provisioning completed", result });
        } catch (e: any) {
            logger.error(`Provisioning error: ${e?.message || e}`);
            res.status(500).json({
                message: "provisioning failed",
                error: e?.message || String(e),
            });
        }
    };

    private async provision(
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
            const per = { index: i + 1, userName: u.userName as string };

            try {
                const desired = toScimUserDoc(u);
                const existing = await findUser(http, u.userName, orgId);

                if (existing) {
                    await updateUser(http, existing.id, desired, orgId);
                    logger.info(
                        `[${i + 1}] UPDATE ${u.userName} (id=${existing.id})`
                    );
                    result.ok++;
                    result.details.push({ ...per, action: "update" });
                } else {
                    const created = await createUser(http, desired, orgId);
                    logger.info(
                        `[${i + 1}] CREATE ${u.userName} (id=${created.id})`
                    );
                    result.ok++;
                    result.details.push({ ...per, action: "create" });
                }
            } catch (e: any) {
                result.details.push({ ...per, error: e?.message || String(e) });
            }
        }

        logger.info(`Provisioning result: ${JSON.stringify(result, null, 2)}`);
        return result;
    }

    private async deprovision(
        orgId: string,
        userNames: string[]
    ): Promise<ProvisionResult> {
        const baseUrl =
            process.env.KEYCLOAK_BASE_URL ?? "http://localhost:8890";
        const result: ProvisionResult = {
            total: userNames.length,
            ok: 0,
            details: [],
        };
        const http = await createHttpclient(this.issuerUrl, baseUrl);

        for (let i = 0; i < userNames.length; i++) {
            const userName = userNames[i];
            const per = { index: i + 1, userName };

            try {
                const existing = await findUser(http, userName, orgId);
                if (existing?.id) {
                    await deleteUser(http, existing.id, orgId);
                    logger.info(
                        `[${i + 1}] DELETE ${userName} (id=${existing.id})`
                    );
                    result.ok++;
                    result.details.push({ ...per, action: "delete" });
                } else {
                    logger.info(
                        `[${i + 1}] DELETE-SKIP ${userName} (not found)`
                    );
                    result.ok++;
                    result.details.push({ ...per, action: "delete-skip" });
                }
            } catch (e: any) {
                result.details.push({ ...per, error: e?.message || String(e) });
            }
        }

        logger.info(
            `Deprovisioning result: ${JSON.stringify(result, null, 2)}`
        );
        return result;
    }
}

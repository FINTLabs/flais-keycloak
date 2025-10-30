import type { AxiosInstance } from "axios";

export interface ScimUserInput {
    externalId: string;
    userName: string;
    active: boolean;
    name: { givenName: string; familyName: string };
    emails: Array<{ value: string; primary?: boolean; type?: string }>;
    groups: string[];
}

const CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
const CORE_GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

export function toScimUserDoc(u: ScimUserInput) {
    const doc: any = {
        schemas: [CORE_USER_SCHEMA],
        userName: u.userName,
        active: u.active ?? true,
    };
    for (const k of [
        "name",
        "displayName",
        "nickName",
        "title",
        "locale",
        "timezone",
        "emails",
        "phoneNumbers",
        "addresses",
        "photos",
        "externalId",
    ] as const) {
        if ((u as any)[k] !== undefined) (doc as any)[k] = (u as any)[k];
    }
    return doc;
}

export async function findUser(
    http: AxiosInstance,
    userName: string,
    orgId: string
) {
    const r = await http.get(
        `/realms/external/scim/v2/organizations/${orgId}/Users`,
        {
            params: { filter: `userName eq \"${userName}\"` },
        }
    );
    if (r.status !== 200)
        throw new Error(
            `Find user failed: ${r.status} ${JSON.stringify(r.data)}`
        );
    const resources = r.data?.Resources ?? [];
    return resources[0] || null;
}

export async function createUser(http: AxiosInstance, doc: any, orgId: string) {
    const r = await http.post(
        `/realms/external/scim/v2/organizations/${orgId}/Users`,
        doc
    );
    if (![200, 201].includes(r.status))
        throw new Error(
            `Create user failed: ${r.status} ${JSON.stringify(r.data)}`
        );
    return r.data;
}

export async function updateUser(
    http: AxiosInstance,
    id: string,
    doc: any,
    orgId: string
) {
    const r = await http.put(
        `/realms/external/scim/v2/organizations/${orgId}/Users/${id}`,
        doc
    );
    if (r.status !== 200)
        throw new Error(
            `Update user failed: ${r.status} ${JSON.stringify(r.data)}`
        );
    return r.data;
}

export async function findGroup(
    http: AxiosInstance,
    displayName: string,
    orgId: string
) {
    const r = await http.get(
        `/realms/external/scim/v2/organizations/${orgId}/Groups`,
        {
            params: { filter: `displayName eq \"${displayName}\"` },
        }
    );
    if (r.status !== 200)
        throw new Error(
            `Find group failed: ${r.status} ${JSON.stringify(r.data)}`
        );
    const resources = r.data?.Resources ?? [];
    return resources[0] || null;
}

export async function createGroup(
    http: AxiosInstance,
    displayName: string,
    orgId: string
) {
    const r = await http.post(
        `/realms/external/scim/v2/organizations/${orgId}/Groups`,
        {
            schemas: [CORE_GROUP_SCHEMA],
            displayName,
        }
    );
    if (![200, 201].includes(r.status))
        throw new Error(
            `Create group failed: ${r.status} ${JSON.stringify(r.data)}`
        );
    return r.data;
}

export async function ensureGroup(
    http: AxiosInstance,
    name: string,
    orgId: string
) {
    const g = await findGroup(http, name, orgId);
    return g ?? (await createGroup(http, name, orgId));
}

export async function addUserToGroup(
    http: AxiosInstance,
    groupId: string,
    userId: string,
    orgId: string
) {
    const payload = {
        schemas: ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
        Operations: [
            {
                op: "Add",
                path: "members",
                value: [{ value: userId }],
            },
        ],
    };
    const r = await http.patch(
        `/realms/external/scim/v2/organizations/${orgId}/Groups/${groupId}`,
        payload
    );
    if (![200, 204].includes(r.status))
        throw new Error(
            `Group membership failed: ${r.status} ${JSON.stringify(r.data)}`
        );
}

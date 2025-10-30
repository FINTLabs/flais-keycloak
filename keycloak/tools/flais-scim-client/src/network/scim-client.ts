import axios, {
    AxiosInstance,
    AxiosRequestHeaders,
    AxiosResponse,
    InternalAxiosRequestConfig,
} from "axios";
import logger from "../logger/logger";
import { randomUUID } from "node:crypto";

export const createHttpclient = async (
    issuerUrl: string,
    baseUrl: string
): Promise<AxiosInstance> => {
    const instance = axios.create({
        baseURL: baseUrl,
        timeout: 10_000,
    });

    const token = await getToken(issuerUrl);

    instance.interceptors.request.use(
        (config: InternalAxiosRequestConfig) => {
            const id = randomUUID();
            (config as any).requestId = id;

            config.headers = (config.headers || {}) as AxiosRequestHeaders;
            config.headers["Accept"] = "application/scim+json";

            const method = (config.method || "get").toLowerCase();
            const hasBody = ["post", "put", "patch"].includes(method);
            if (hasBody) {
                config.headers["Content-Type"] = "application/scim+json";
            }

            if (token) {
                config.headers["Authorization"] = `Bearer ${token}`;
            }

            const loggedHeaders: Record<string, unknown> = {
                ...config.headers,
                Authorization: config.headers["Authorization"]
                    ? "Bearer ***"
                    : undefined,
            };

            logger.info(
                JSON.stringify({
                    type: "http_request",
                    id,
                    method: method.toUpperCase(),
                    url: `${config.baseURL ?? ""}${config.url ?? ""}`,
                    params: config.params ?? null,
                    headers: loggedHeaders,
                    data: config.data ?? null,
                    timeout: config.timeout,
                })
            );

            return config;
        },
        (error) => {
            logger.error(
                JSON.stringify({
                    type: "http_request_error",
                    error: error.message,
                })
            );
            return Promise.reject(error);
        }
    );

    instance.interceptors.response.use(
        (response: AxiosResponse) => {
            const id = (response.config as any)?.requestId;
            logger.info(
                JSON.stringify({
                    type: "http_response",
                    id,
                    status: response.status,
                    url: `${response.config.baseURL ?? ""}${
                        response.config.url ?? ""
                    }`,
                    headers: response.headers,
                    data: response.data,
                })
            );
            return response;
        },
        (error: any) => {
            const id = (error.config as any)?.requestId;

            const url = `${error.config?.baseURL ?? ""}${
                error.config?.url ?? ""
            }`;
            if (error.response) {
                logger.error(
                    JSON.stringify({
                        type: "http_response_error",
                        id,
                        status: error.response.status,
                        url,
                        headers: error.response.headers,
                        data: error.response.data,
                    })
                );
            } else {
                logger.error(
                    JSON.stringify({
                        type: "http_network_error",
                        id,
                        url,
                        code: error.code,
                        message: error.message,
                    })
                );
            }
            return Promise.reject(error);
        }
    );

    return instance;
};

const ensureHttpUrl = (u: string) =>
    /^https?:\/\//i.test(u) ? u : `http://${u}`;

const getToken = async (issuerUrl: string) => {
    const tokenUrl = new URL("/token", ensureHttpUrl(issuerUrl)).toString();

    logger.info(`Fetching token from: ${tokenUrl}`);
    try {
        const res = await axios.post(
            tokenUrl,
            {},
            { timeout: 5000, headers: { Accept: "application/json" } }
        );
        const token = res.data?.access_token ?? res.data?.token;
        return token || "";
    } catch (e: any) {
        logger.error(`TOKEN ERROR: ${e.message}`);
        return "";
    }
};

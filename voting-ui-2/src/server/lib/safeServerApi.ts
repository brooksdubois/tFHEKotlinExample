// server/lib/safeServerApi.ts
import { TRPCError } from "@trpc/server";
import { ApiClient, type RequestOptions } from "~/lib/rxApi";

function toTrpc(err: unknown, fallback: string) {
    const message = (err as any)?.message ?? fallback;
    const name = (err as any)?.name;
    const code = name === "TimeoutError" ? "TIMEOUT" : "INTERNAL_SERVER_ERROR";
    return new TRPCError({ code: code as any, message });
}

function joinQuery(endpoint: string, query?: Record<string, unknown>): string {
    if (!query) return endpoint;
    const sp = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
        if (v == null) continue;
        sp.set(k, String(v));
    }
    return `${endpoint}${endpoint.includes("?") ? "&" : "?"}${sp.toString()}`;
}

export class SafeServerApi {
    constructor(
        private readonly http: ApiClient,
        private readonly baseUrl: string,
        private readonly defaultTimeoutMs = 10_000
    ) {}

    async get<T>(args: {
        endpoint: string;
        errorMessage?: string;
        query?: Record<string, unknown>;
    } & Omit<RequestOptions, "method" | "body">): Promise<T> {
        const { endpoint, errorMessage = "request failed", query, ...opts } = args;
        try {
            return await this.http.get<T>(joinQuery(endpoint, query), opts);
        } catch (err) {
            throw toTrpc(err, errorMessage);
        }
    }

    async post<T>(args: {
        endpoint: string;
        body?: unknown;
        errorMessage?: string;
    } & Omit<RequestOptions, "method">): Promise<T> {
        const { endpoint, body, errorMessage = "request failed", ...opts } = args;
        try {
            return await this.http.post<T>(endpoint, body, opts);
        } catch (err) {
            throw toTrpc(err, errorMessage);
        }
    }

    /**
     * Plain-text GET (e.g., JSONL/proof streams) with the same error/timeout semantics.
     * Uses native fetch here because ApiClient parses JSON for .get<T>().
     */
    async getText(args: {
        endpoint: string;
        errorMessage?: string;
        query?: Record<string, unknown>;
        /** Optional per-call timeout override (ms). Falls back to defaultTimeoutMs. */
        timeoutMs?: number;
        /** Optional headers to pass (rarely needed). */
        headers?: Record<string, string>;
    }): Promise<string> {
        const {
            endpoint,
            errorMessage = "request failed",
            query,
            timeoutMs,
            headers,
        } = args;

        const url = `${this.baseUrl}${joinQuery(endpoint, query)}`;
        const controller = new AbortController();
        const to = window.setTimeout(
            () => controller.abort(),
            timeoutMs ?? this.defaultTimeoutMs ?? 0
        );

        try {
            const res = await fetch(url, {
                method: "GET",
                headers,
                signal: controller.signal,
            });
            if (!res.ok) {
                throw new Error(`${errorMessage}: ${res.status}`);
            }
            return await res.text();
        } catch (err) {
            throw toTrpc(err, errorMessage);
        } finally {
            clearTimeout(to);
        }
    }

    async getBinary(args: {
        endpoint: string;
        errorMessage?: string;
        query?: Record<string, unknown>;
        timeoutMs?: number;
        headers?: Record<string, string>;
    }): Promise<Uint8Array> {
        const { endpoint, errorMessage = "request failed", query, timeoutMs, headers } = args;
        const url = `${this.baseUrl}${joinQuery(endpoint, query)}`;
        const controller = new AbortController();
        const to = setTimeout(() => controller.abort(), timeoutMs ?? this.defaultTimeoutMs ?? 0);
        try {
            const res = await fetch(url, { method: "GET", headers, signal: controller.signal });
            if (!res.ok) throw new Error(`${errorMessage}: ${res.status}`);
            return new Uint8Array(await res.arrayBuffer());
        } catch (err) {
            throw toTrpc(err, errorMessage);
        } finally { clearTimeout(to); }
    }
}

export const createSafeServerApi = (baseUrl: string, defaultTimeoutMs?: number) =>
    new SafeServerApi(new ApiClient(baseUrl, defaultTimeoutMs), baseUrl, defaultTimeoutMs);

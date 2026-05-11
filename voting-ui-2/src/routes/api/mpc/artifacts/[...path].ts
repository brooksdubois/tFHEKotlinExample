// src/routes/api/mpc/artifacts/[...path].ts
import type { APIEvent } from "@solidjs/start/server";

const HOST = process.env.KTOR_HOST ?? "http://localhost:8080";

export async function GET({ params }: APIEvent) {
    // params.path can be string | string[] | undefined
    const raw = (params as any)?.path;
    const segs =
        Array.isArray(raw) ? raw :
            typeof raw === "string" ? raw.split("/") :
                [];

    if (segs.length === 0) {
        return new Response("Bad path", { status: 400 });
    }

    // Re-encode each segment to be safe
    const url = `${HOST}/mpc/artifacts/${segs.map(s => encodeURIComponent(s)).join("/")}`;

    const resp = await fetch(url);
    return new Response(resp.body, {
        status: resp.status,
        headers: {
            "content-type": resp.headers.get("content-type") ?? "application/octet-stream",
            "cache-control": "no-store",
        },
    });
}

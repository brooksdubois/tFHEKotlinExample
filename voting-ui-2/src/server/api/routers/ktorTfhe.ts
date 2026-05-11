// src/server/api/routers/ktorTfhe.ts
import { z } from "zod";
import { createTRPCRouter, publicProcedure } from "../utils";
import { createSafeServerApi } from "~/server/lib/safeServerApi";
import JSZip from "jszip";

// Allow either KTOR_HOST (preferred) or legacy BB_HOST
const HOST = process.env.KTOR_HOST ?? "http://localhost:8080";
const api = createSafeServerApi(HOST, 2000);

/** ---- Schemas that mirror your Ktor responses ---- */

// POST /vote input (new)
const VoteIn = z.object({
    id: z.string().min(1),
    name: z.string().min(1),
    address: z.string().min(1),
    age: z.number().int().min(0),
    candidate: z.number().int().min(0),
    /** optional, per-voter receipt ciphertext (u16) */
    receiptCtB64: z.string().optional(),
});

// GET /blocks output
const RecordSummary = z.object({
    id: z.string(),
    name: z.string(),
    address: z.string(),
    age: z.number(),
    timestamp: z.number(),
    commitment: z.string(),
});
const BlockSummary = z.object({
    index: z.number(),
    recordCount: z.number(),
    records: z.array(RecordSummary),
});
const BlocksOut = z.array(BlockSummary);

// Verifier feed: GET /user-votes
const UserVotesOut = z.array(z.array(z.string())); // [[base64, ...], ...]


// --- receipt-by-id shapes coming from Ktor ---
const ReceiptBitsShape = z.object({
    id: z.string(),
    receiptBitsB64: z.array(z.string()),
}).passthrough();

const ReceiptStringShape = z.object({
    id: z.string(),
    receiptB64: z.string().nullable(),
}).passthrough();

const ReceiptByIdRaw = z.union([ReceiptBitsShape, ReceiptStringShape]);

// --- the shape your UI will actually use ---
const ReceiptOut = z.object({
    id: z.string(),
    receiptBitsB64: z.array(z.string()).optional(),
});

const CastAck = z.object({
    ok: z.boolean(),
    candidate: z.number().int(),
    recordId: z.string(),
    receiptBitsB64: z.array(z.string()).optional(),  // ← array
});

const VoteOutShape = z.object({
    ok: z.boolean(),
    candidate: z.number().int(),
    recordId: z.string(),
    receiptBitsB64: z.array(z.string()).optional(),  // ← array
}).passthrough();

const MpcStartReq = z.object({
    source: z.enum(["live", "upload"]),
    ctsB64: z.array(z.string()).optional(),
});
const MpcStartRes = z.object({
    id: z.string(),
    candidateCount: z.number(),
    artifacts: z.array(z.string()),
});

// ---- new MPC shapes for follow-up steps ----
const MpcMaskReq = z.object({
    id: z.string().min(1),
    who: z.string().min(1),
    seed: z.number().int().optional(),
});
const MpcCommit = z.object({
    seq: z.number().int(),
    who: z.string(),
    masksHashHex: z.string(),
});
const MpcMaskOut = z.object({ commit: MpcCommit });

const MpcIdOnly = z.object({ id: z.string().min(1) });

const MpcFetchMasksReq = z.object({
    id: z.string().min(1),
    who: z.string().min(1),
});
const MpcFetchMasksOut = z.object({ masks: z.array(z.number().int()) });

const MpcRevealReq = z.object({
    id: z.string().min(1),
    who: z.string().min(1),
    masks: z.array(z.number().int()),
});
const MpcRevealOut = z.object({
    totals: z.array(z.number().int()),
    finalized: z.boolean().optional(),
});

function normalizeAck(raw: unknown, input: z.infer<typeof VoteIn>) {
    const d: any = raw ?? {};
    return {
        ok: typeof d.ok === "boolean" ? d.ok : true,
        candidate: Number.isFinite(d.candidate) ? d.candidate : input.candidate,
        recordId: d.recordId ?? d.id ?? d.trackerId ?? input.id,
        receiptBitsB64: Array.isArray(d.receiptBitsB64)
            ? d.receiptBitsB64
            : undefined,
    };
}

export const ktorTfhe = createTRPCRouter({
    /** GET "/" — health (200 OK is enough) */
    health: publicProcedure.query(async () => {
        await api.get<void>({ endpoint: "/", errorMessage: "backend health failed" });
        return { ok: true };
    }),

    vote: publicProcedure
        .input(VoteIn)
        .mutation(async ({ input }) => {
            const out = await api.post({
                endpoint: "/vote",
                body: input,
                errorMessage: "vote submit failed",
            });
            const parsed = VoteOutShape.safeParse(out);
            const norm = normalizeAck(parsed.success ? parsed.data : out, input);
            return CastAck.parse(norm);
        }),

    /** GET /user-votes — unchanged */
    userVotes: publicProcedure.query(async () => {
        const out = await api.get({
            endpoint: "/user-votes",
            errorMessage: "user votes fetch failed",
        });
        return UserVotesOut.parse(out);
    }),

    /** GET /blocks + local lookup by tracker id */
    lookup: publicProcedure
        .input(z.object({ trackerId: z.string().min(1) }))
        .query(async ({ input }) => {
            const blocks = BlocksOut.parse(
                await api.get({ endpoint: "/blocks", errorMessage: "blocks fetch failed" })
            );
            const hit = blocks.flatMap(b => b.records).find(r => r.id === input.trackerId);
            if (!hit) return { status: "NOT_FOUND" as const, record: null };
            return { status: "INCLUDED" as const, record: hit };
        }),

    receiptById: publicProcedure
        .input(z.object({ id: z.string().min(1) }))
        .query(async ({ input }) => {
            const endpoint = `/receipt/${encodeURIComponent(input.id)}`;
            try {
                const raw = await api.get({ endpoint, errorMessage: "receipt fetch failed" });
                const parsed = ReceiptByIdRaw.parse(raw);
                // normalize to bits-array once, without ternaries everywhere
                const out =
                    "receiptBitsB64" in parsed
                        ? { id: parsed.id, receiptBitsB64: parsed.receiptBitsB64 }
                        : { id: parsed.id, receiptBitsB64: parsed.receiptB64 ? [parsed.receiptB64] : undefined };
                return ReceiptOut.parse(out);
            } catch (err: any) {
                const msg = String(err?.message || err);
                if (msg.includes("404")) return { id: input.id, receiptBitsB64: undefined };
                throw err; // let tRPC surface the real error
            }
        }),

    blocks: publicProcedure.query(async () => {
        const out = await api.get({
            endpoint: "/blocks",
            errorMessage: "blocks fetch failed",
        });
        return BlocksOut.parse(out);
    }),

    mpcStartSession: publicProcedure
        .input(MpcStartReq)
        .mutation(async ({ input }) => {
            const out = await api.post({
                endpoint: "/mpc/sessions/start",
                body: input,
                errorMessage: "mpc start failed",
                timeoutMs: 45_000,
            });
            return MpcStartRes.parse(out);
        }),

    /** Step 1 — server mask commit */
    mpcMaskServer: publicProcedure
        .input(MpcMaskReq)
        .mutation(async ({ input }) => {
            const out = await api.post({
                endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}/mask:server`,
                body: { who: input.who, seed: input.seed },
                errorMessage: "mpc mask failed",
            });
            return MpcMaskOut.parse(out);
        }),

    /** Step 2 — decrypt masked totals */
    mpcDecrypt: publicProcedure
        .input(MpcIdOnly)
        .mutation(async ({ input }) => {
            // passthrough result (shape is not used by UI right now)
            return await api.post({
                endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}/decrypt`,
                body: {},
                errorMessage: "mpc decrypt failed",
            });
        }),

    /** Step 3 — fetch ZIP and extract masks_<who>.json */
    mpcFetchMasks: publicProcedure
        .input(MpcFetchMasksReq)
        .query(async ({ input }) => {
            const zipBytes = await api.getBinary({
                endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}/zip`,
                errorMessage: "mpc zip fetch failed",
            });
            const zip = await JSZip.loadAsync(zipBytes);
            const fname = `masks_${input.who}.json`;
            const file = zip.file(fname);
            if (!file) throw new Error(`ZIP missing ${fname}`);
            const text = await file.async("string");
            const masks = JSON.parse(text);
            return MpcFetchMasksOut.parse({ masks });
        }),

    /** Step 4 — reveal */
    mpcReveal: publicProcedure
        .input(MpcRevealReq)
        .mutation(async ({ input }) => {
            const out = await api.post({
                endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}/reveal`,
                body: { who: input.who, masks: input.masks },
                errorMessage: "mpc reveal failed",
            });
            return MpcRevealOut.parse(out);
        }),
    /** Lightweight session status (commits, reveals, candidateCount, etc.) */
    mpcStatus: publicProcedure
        .input(MpcIdOnly)
        .query(async ({ input }) => {
            return await api.get({
                endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}`,
                errorMessage: "mpc status failed",
            });
        }),
    /** Read current totals; returns zeros before reveal to keep UI stable. */
    mpcTotals: publicProcedure
        .input(MpcIdOnly)
        .query(async ({ input }) => {
            // Try reading the computed totals
            try {
                const text = await api.getText({
                    endpoint: `/mpc/artifacts/${encodeURIComponent(input.id)}/totals.json`,
                    errorMessage: "totals fetch failed",
                    timeoutMs: 20_000,
                });
                const arr = JSON.parse(text) as number[];
                const total = arr.reduce((a, b) => a + b, 0);
                const counts: Record<number, number> = {};
                for (let i = 0; i < arr.length; i++) counts[i] = arr[i];
                return { counts, total, finalized: true };
            } catch {
                // Not revealed yet: infer candidate count from status and return zeros
                const status = await api.get({
                    endpoint: `/mpc/sessions/${encodeURIComponent(input.id)}`,
                    errorMessage: "mpc status failed",
                });
                const n = Number(status?.candidateCount ?? 0);
                const counts: Record<number, number> = {};
                for (let i = 0; i < n; i++) counts[i] = 0;
                return { counts, total: 0, finalized: false };
            }
        }),
});

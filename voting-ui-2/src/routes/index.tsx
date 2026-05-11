import { For, Match, Switch, Show, createEffect, createSignal, createMemo } from "solid-js";
import { api } from "~/lib/api";
import { useOnline } from "~/shared/Online";
import { useRxFetch } from "~/shared/useRxFetch";

// Local type: only what we need to match/highlight from the uploaded receipt
type VoteReceipt = {
    id: string;
    commitment: string;
    clientKeyB64: string;
    receiptBitsB64: string[];
};

// Ktor block summaries (match zod types from router)
type RecordSummary = {
    id: string;
    name: string;
    address: string;
    age: number;
    timestamp: number;
    commitment: string;
};
type BlockSummary = {
    index: number;
    recordCount: number;
    records: RecordSummary[];
};

export default function Home() {
    const { online } = useOnline();

    // Encrypted ballots only (no metadata) — [[base64,...], ...]
    const [votes, { loading: votesLoading, refetch: refetchVotes }] =
        useRxFetch<string[][] | null>(
            async () => (!import.meta.env.SSR && online() ? api.backend.userVotes.query() : null),
            () => online()
        );

    // Ledger blocks (metadata only)
    const [blocks, { loading: blocksLoading, refetch: refetchBlocks }] =
        useRxFetch<BlockSummary[] | null>(
            async () => (!import.meta.env.SSR && online() ? api.backend.blocks.query() : null),
            () => online()
        );

    // Upload + match state
    const [receipt, setReceipt] = createSignal<VoteReceipt | null>(null);
    const [matchedIndex, setMatchedIndex] = createSignal<number | null>(null);

    // Order-sensitive equality for receipt matching
    const eqArr = (a: string[], b: string[]) => a.length === b.length && a.every((v, i) => v === b[i]);

    // Flatten records and zip with ciphertext arrays by index
    const rows = createMemo(() => {
        const b = blocks();
        const v = votes();
        if (!b || !v) return [];
        const recs = b.flatMap(bl => bl.records);
        return recs.map((rec, i) => ({ rec, ct: v[i] ?? [] as string[] }));
    });

    const lengthMismatch = createMemo(() => {
        const b = blocks();
        const v = votes();
        if (!b || !v) return false;
        const recCount = b.reduce((sum, bl) => sum + bl.recordCount, 0);
        return recCount !== v.length;
    });

    // When receipt or votes change, re-match and scroll
    createEffect(() => {
        const r = receipt();
        const v = votes();
        if (!r || !v) {
            setMatchedIndex(null);
            return;
        }
        const idx = v.findIndex(entry => eqArr(entry, r.receiptBitsB64));
        setMatchedIndex(idx >= 0 ? idx : null);

        if (idx >= 0) {
            queueMicrotask(() => {
                const el = document.getElementById(`vote-${idx}`);
                el?.scrollIntoView({ behavior: "smooth", block: "center" });
            });
        }
    });

    // File upload handler
    const onUpload = async (e: Event) => {
        const input = e.currentTarget as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        try {
            const json = JSON.parse(await file.text());
            if (!Array.isArray(json?.receiptBitsB64)) throw new Error("Bad receipt");
            setReceipt(json as VoteReceipt);
        } catch {
            setReceipt(null);
            setMatchedIndex(null);
            alert("Could not parse receipt JSON.");
        } finally {
            input.value = "";
        }
    };

    const copyJson = async () => {
        const data = rows();
        if (!data.length) return;
        try {
            const exportable = data.map(({ rec, ct }) => ({
                id: rec.id,
                timestamp: rec.timestamp,
                commitment: rec.commitment,
                ciphertexts: ct,
            }));
            await navigator.clipboard.writeText(JSON.stringify(exportable, null, 2));
        } catch { /* noop */ }
    };

    const refreshAll = () => {
        refetchVotes();
        refetchBlocks();
    };

    const anyLoading = () => votesLoading() || blocksLoading();

    return (
        <main class="container mx-auto px-4 xl:px-[10%] 2xl:px-[12%] my-8">
            <header class="flex items-center justify-between">
                <h1 class="text-2xl font-semibold">Voting Demo</h1>
                <div class="text-sm opacity-70">
                    <Show when={online()} fallback={<span>Offline</span>}>Online</Show>
                </div>
            </header>

            <section class="space-y-3 mt-6">
                <h2 class="text-base font-medium">Encrypted Ledger (blocks + ciphertexts)</h2>
                <p class="text-sm opacity-80">
                    Each row is a recorded vote: metadata from the ledger paired with its Base64-encoded ciphertext array.
                    Upload your receipt to highlight your row.
                </p>

                <div class="flex flex-wrap gap-2 items-center">
                    <button class="btn" onClick={refreshAll}>Refresh</button>
                    <button class="btn" disabled={!rows().length || anyLoading()} onClick={copyJson}>
                        Copy JSON
                    </button>
                    <label class="btn cursor-pointer">
                        Upload Receipt (.json)
                        <input type="file" accept="application/json" class="hidden" onChange={onUpload} />
                    </label>

                    <Show when={receipt()}>
                        {(r) => (
                            <span class="text-sm px-2 py-1 rounded bg-black/5">
                Receipt loaded ({r().receiptBitsB64.length} segments)
              </span>
                        )}
                    </Show>

                    <Show when={matchedIndex() !== null}>
            <span class="text-sm px-2 py-1 rounded bg-green-600/10 text-green-700">
              Match: index {matchedIndex()}
            </span>
                    </Show>

                    <Show when={receipt() && matchedIndex() === null && !anyLoading()}>
            <span class="text-sm px-2 py-1 rounded bg-red-600/10 text-red-700">
              No match found
            </span>
                    </Show>

                    <Show when={lengthMismatch()}>
            <span class="text-sm px-2 py-1 rounded bg-red-600/10 text-red-700">
              Warning: block record count and vote list length differ
            </span>
                    </Show>
                </div>

                <Switch fallback={anyLoading() ? <p class="opacity-60">Loading…</p> : <p class="opacity-60">—</p>}>
                    {/* Empty state */}
                    <Match when={!anyLoading() && rows().length === 0}>
                        <div class="rounded bg-black/5 p-3 text-sm">No entries yet.</div>
                    </Match>

                    {/* Rows */}
                    <Match when={rows().length ? rows() : null}>
                        {(list) => (
                            <ul class="space-y-2 max-h-[60vh] overflow-auto pr-1">
                                <For each={list()}>
                                    {(row, i) => {
                                        const isMatch = () => matchedIndex() === i();
                                        return (
                                            <li
                                                id={`vote-${i()}`}
                                                class={`rounded p-2 border transition-shadow ${
                                                    isMatch() ? "border-green-600 ring-2 ring-green-600/40 bg-green-600/5" : "border-black/10 bg-black/5"
                                                }`}
                                            >
                                                <div class="text-[10px] opacity-70 mb-1">#{i()}</div>
                                                <div class="text-xs grid gap-1 sm:grid-cols-2">
                                                    <div class="truncate"><b>Tracker ID:</b> <span class="break-all">{row.rec.id}</span></div>
                                                    <div><b>Timestamp:</b> {new Date(row.rec.timestamp * 1000).toLocaleString()}</div>
                                                    <div class="truncate sm:col-span-2"><b>Commitment:</b> <span class="break-all">{row.rec.commitment}</span></div>
                                                </div>
                                                <pre class="font-mono text-[11px] overflow-auto mt-1">{JSON.stringify(row.ct, null, 2)}</pre>
                                            </li>
                                        );
                                    }}
                                </For>
                            </ul>
                        )}
                    </Match>
                </Switch>
            </section>
        </main>
    );
}

// src/routes/mpc.tsx
import { createMemo, createSignal, Show, For } from "solid-js";
import { api } from "~/lib/api";

export default function Mpc() {
    // ---- shared state
    const [id, setId] = createSignal("");
    const [who, setWho] = createSignal("verifier-1");
    const [seed, setSeed] = createSignal(1337);
    const [logs, setLogs] = createSignal<string[]>([]);
    const [totals, setTotals] = createSignal<number[] | null>(null);
    const [finalized, setFinalized] = createSignal<boolean | null>(null);
    const [busy, setBusy] = createSignal(false);
    const log = (s: string) => setLogs(v => [...v, s]);

    // ---- actions
    const startLive = async () => {
        setBusy(true); setTotals(null); setFinalized(null);
        try {
            log("Starting session from live ledger…");
            const out = await api.backend.mpcStartSession.mutate({ source: "live" });
            setId(out.id);
            log(`Started session: ${out.id} (choices=${out.candidateCount})`);
        } catch (e: any) {
            log(`ERROR(start): ${e?.message ?? String(e)}`);
        } finally {
            setBusy(false);
        }
    };

    const runSteps = async () => {
        setBusy(true); setTotals(null); setFinalized(null);
        try {
            const sid = id().trim();
            if (!sid) throw new Error("Enter a session id");
            log(`Session: ${sid}`);

            // 1) mask
            const m1 = await api.backend.mpcMaskServer.mutate({ id: sid, who: who(), seed: seed() });
            log(`Masked (seq=${m1.commit.seq}, hash=${m1.commit.masksHashHex.slice(0,12)}…)`);

            // 2) decrypt masked totals
            await api.backend.mpcDecrypt.mutate({ id: sid });
            log("Decrypted masked totals");

            // 3) fetch+extract masks
            const m2 = await api.backend.mpcFetchMasks.query({ id: sid, who: who() });
            log(`Extracted ${m2.masks.length} masks`);

            // 4) reveal
            const r = await api.backend.mpcReveal.mutate({ id: sid, who: who(), masks: m2.masks });
            setTotals(r.totals);
            setFinalized(!!r.finalized);
            log(`Reveal → totals=${JSON.stringify(r.totals)} finalized=${String(r.finalized ?? false)}`);
        } catch (e: any) {
            log(`ERROR(flow): ${e?.message ?? String(e)}`);
        } finally {
            setBusy(false);
        }
    };

    const startAndRun = async () => {
        await startLive();
        if (id()) await runSteps();
    };

    // ---- UI
    return (
        <main class="container mx-auto px-4 xl:px-[10%] 2xl:px-[12%] my-8 space-y-6">
            <div class="flex items-center justify-between">
                <h1 class="text-2xl font-semibold">MPC — Start & Continue</h1>
                <Show when={finalized() !== null}>
          <span class={`px-2 py-1 rounded text-xs ${finalized() ? "bg-emerald-100" : "bg-amber-100"}`}>
            {finalized() ? "FINALIZED" : "REVEALED (awaiting more)"}
          </span>
                </Show>
            </div>

            {/* Start session (live) */}
            <div class="card space-y-3">
                <div class="flex items-center justify-between">
                    <p class="font-medium">Start new session (live ledger)</p>
                    <div class="flex gap-2">
                        <button
                            class={`btn ${busy() ? "opacity-50 cursor-not-allowed" : ""}`}
                            disabled={busy()}
                            onClick={startLive}
                            title="POST /mpc/sessions/start { source: 'live' }"
                        >
                            {busy() ? "Working…" : "Start"}
                        </button>
                        <button
                            class={`btn ${busy() ? "opacity-50 cursor-not-allowed" : ""}`}
                            disabled={busy()}
                            onClick={startAndRun}
                            title="Start and run mask→decrypt→masks→reveal"
                        >
                            {busy() ? "Working…" : "Start & Run"}
                        </button>
                    </div>
                </div>

                <div class="grid gap-3 sm:grid-cols-3">
                    <label class="flex flex-col text-sm">
                        <span class="mb-1">Session ID</span>
                        <input
                            class="input"
                            placeholder="mpc-xxxx"
                            value={id()}
                            onInput={e => setId(e.currentTarget.value)}
                        />
                    </label>
                    <label class="flex flex-col text-sm">
                        <span class="mb-1">Who</span>
                        <input class="input" value={who()} onInput={e => setWho(e.currentTarget.value)} />
                    </label>
                    <label class="flex flex-col text-sm">
                        <span class="mb-1">Seed</span>
                        <input
                            class="input"
                            type="number"
                            value={seed()}
                            onInput={e => setSeed(+e.currentTarget.value)}
                        />
                    </label>
                </div>

                <div class="flex items-center gap-2">
                    <button
                        class={`btn ${busy() || !id().trim() ? "opacity-50 cursor-not-allowed" : ""}`}
                        disabled={busy() || !id().trim()}
                        onClick={runSteps}
                    >
                        {busy() ? "Running…" : "Run next steps (mask → decrypt → masks → reveal)"}
                    </button>

                    <Show when={id().trim()}>
                        <a
                            class="btn"
                            href={`/mpc/sessions/${encodeURIComponent(id().trim())}/zip`}
                            target="_blank"
                            rel="noreferrer"
                        >
                            Download ZIP
                        </a>
                    </Show>
                </div>
            </div>

            {/* Fancy tally */}
            <Show when={totals()}>
                <TallyView totals={totals()!} />
            </Show>

            {/* Log */}
            <div class="card">
                <div class="text-sm font-medium mb-2">Log</div>
                <pre class="text-xs whitespace-pre-wrap opacity-80">{logs().join("\n")}</pre>
            </div>
        </main>
    );
}

/* ---------- Local, self-contained tally view ---------- */
function TallyView(props: { totals: number[] }) {
    const counts = createMemo(() => props.totals ?? []);
    const totalVotes = createMemo(() => counts().reduce((a, b) => a + b, 0));
    const gridCols = createMemo(() => Math.max(1, counts().length));

    return (
        <>
            <div class="card">
                <div class="flex items-center justify-between">
                    <p class="font-medium">Total Votes</p>
                    <p class="text-2xl font-semibold tabular-nums">{totalVotes()}</p>
                </div>
            </div>

            <div
                class="grid gap-3"
                style={{ "grid-template-columns": `repeat(${gridCols()}, minmax(0, 1fr))` }}
            >
                <For each={counts()}>
                    {(c, i) => {
                        const pct = () => (totalVotes() > 0 ? Math.round((c / totalVotes()) * 100) : 0);
                        return (
                            <div class="card space-y-2">
                                <div class="flex items-baseline justify-between">
                                    <p class="font-medium">Choice {i() + 1}</p>
                                    <p class="font-semibold tabular-nums">{c}</p>
                                </div>
                                <div class="h-2 rounded bg-black/10 overflow-hidden">
                                    <div
                                        class="h-full bg-black/70 transition-[width] duration-300"
                                        style={{ width: `${pct()}%` }}
                                    />
                                </div>
                                <p class="text-xs muted">{pct()}%</p>
                            </div>
                        );
                    }}
                </For>
            </div>
        </>
    );
}

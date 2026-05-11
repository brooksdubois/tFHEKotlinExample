// src/routes/verify.tsx
import { createSignal, Show, createMemo } from "solid-js";

// ---- WASM loader (same pattern as computeTally.tsx) ----
let _tfhe: any | null = null;
async function loadTfhe(): Promise<any> {
    if (_tfhe) return _tfhe;
    const mod: any = await import("tfhe");

    const initFn =
        (typeof mod?.default === "function" && mod.default) ||
        (typeof mod?.init === "function" && mod.init) ||
        (typeof mod?.initSync === "function" && mod.initSync);
    if (typeof initFn !== "function") throw new Error("tfhe init function not found");

    // SolidStart BASE_URL fix + absolute fallback (identical logic to computeTally)
    const base = ((import.meta as any).env?.BASE_URL ?? "/").replace(/\/?$/, "/");
    const tryPaths = [`${base}vendor/tfhe_bg.wasm`, `/vendor/tfhe_bg.wasm`];

    let inited = false, lastErr: unknown;
    for (const p of tryPaths) {
        try { await initFn({ module_or_path: p }); inited = true; break; } catch (e) { lastErr = e; }
    }
    if (!inited) throw lastErr ?? new Error("Failed to init tfhe wasm");

    if (typeof mod.init_panic_hook === "function") await mod.init_panic_hook();
    _tfhe = mod;
    return _tfhe;
}

// ---- robust Base64: same style as computeTally (handles url-safe + padding) ----
const b64ToBytes = (b64: string) => {
    const norm = b64.replace(/-/g, "+").replace(/_/g, "/");
    const pad = "=".repeat((4 - (norm.length % 4 || 4)) % 4);
    const s = norm + pad;
    return Uint8Array.from(atob(s), c => c.charCodeAt(0));
};

// ---- Types ----
type TfheProof = { recordId: string; ctB64: string };

export default function Verify() {
    // File state (mirrors computeTally’s “clientKey/receipt” pattern)
    const [keyName, setKeyName] = createSignal<string | null>(null);
    const [keyBytes, setKeyBytes] = createSignal<Uint8Array | null>(null);

    const [proofName, setProofName] = createSignal<string | null>(null);
    const [proof, setProof] = createSignal<TfheProof | null>(null);

    const [loading, setLoading] = createSignal(false);
    const [err, setErr] = createSignal<string | null>(null);
    const [out, setOut] = createSignal<{ recordId: string; candidate: number } | null>(null);

    const ready = createMemo(() => !!keyBytes() && !!proof());

    // Upload handlers (same UX shape as computeTally)
    const onUploadKey = async (e: Event) => {
        const input = e.currentTarget as HTMLInputElement;
        const f = input.files?.[0]; if (!f) return;
        setKeyName(f.name);
        setKeyBytes(new Uint8Array(await f.arrayBuffer()));
        input.value = "";
    };

    const onUploadProof = async (e: Event) => {
        const input = e.currentTarget as HTMLInputElement;
        const f = input.files?.[0]; if (!f) return;
        try {
            const json = JSON.parse(await f.text());
            if (typeof json?.recordId !== "string" || typeof json?.ctB64 !== "string") throw new Error("Bad proof");
            setProof(json as TfheProof);
            setProofName(f.name);
        } catch {
            setProof(null);
            setProofName(null);
            alert("Could not parse TFHE proof JSON.");
        } finally {
            input.value = "";
        }
    };

    // Decrypt (Shortint): deserialize client key + ciphertext, then Shortint.decrypt(...)
    const runDecrypt = async () => {
        if (import.meta.env.SSR) return;
        setErr(null); setLoading(true); setOut(null);
        try {
            const tfhe = await loadTfhe();
            const key = keyBytes(); const p = proof();
            if (!key || !p) throw new Error("Upload TFHE key and proof first");

            const cks = tfhe.Shortint.deserialize_client_key(key);
            const ct  = tfhe.Shortint.deserialize_ciphertext(b64ToBytes(p.ctB64));
            const value = Number(tfhe.Shortint.decrypt(cks, ct)); // BigInt -> number
            setOut({ recordId: p.recordId, candidate: value });
        } catch (e: any) {
            setErr(e?.message ?? String(e));
        } finally {
            setLoading(false);
        }
    };

    // icons (copied styling from computeTally)
    const Check = () => (
        <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-7.25 7.25a1 1 0 01-1.414 0l-3-3a1 1 0 111.414-1.414l2.293 2.293 6.543-6.543a1 1 0 011.414 0z" clip-rule="evenodd"/>
        </svg>
    );
    const Dot = () => (
        <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <circle cx="10" cy="10" r="4" />
        </svg>
    );

    return (
        <main class="container mx-auto px-4 xl:px-[10%] 2xl:px-[12%] my-8">
            <h1 class="text-2xl font-semibold">Verify your vote (TFHE self-proof)</h1>
            <p class="text-sm opacity-80 mt-2">Runs in your browser. Your personal key never leaves the page.</p>

            {/* Checklist — EXACT same look/feel as computeTally */}
            <section class="mt-6 rounded-lg border border-black/10 bg-black/5 p-4">
                <h2 class="text-base font-medium mb-3">Checklist</h2>
                <ul class="space-y-3">
                    <li class="flex items-center gap-3">
            <span class={`inline-flex items-center justify-center rounded-full ${
                keyBytes() ? "bg-green-600/15 text-green-700" : "bg-black/10 text-black/60"
            } h-6 w-6`}>
              {keyBytes() ? <Check /> : <Dot />}
            </span>
                        <div class="flex-1">
                            <div class="text-sm font-medium">Upload TFHE Key (.bin)</div>
                            <Show when={keyName()}>
                                {(n) => <div class="mt-1 text-xs"><span class="rounded bg-black/10 px-2 py-0.5">{n()}</span></div>}
                            </Show>
                        </div>
                        <label class="btn cursor-pointer shrink-0">
                            Choose File
                            <input type="file" accept=".bin,application/octet-stream" class="hidden" onChange={onUploadKey} />
                        </label>
                    </li>

                    <li class="flex items-center gap-3">
            <span class={`inline-flex items-center justify-center rounded-full ${
                proof() ? "bg-green-600/15 text-green-700" : "bg-black/10 text-black/60"
            } h-6 w-6`}>
              {proof() ? <Check /> : <Dot />}
            </span>
                        <div class="flex-1">
                            <div class="text-sm font-medium">Upload TFHE Proof (.json)</div>
                            <Show when={proofName()}>
                                {(n) => <div class="mt-1 text-xs"><span class="rounded bg-black/10 px-2 py-0.5">{n()}</span></div>}
                            </Show>
                        </div>
                        <label class="btn cursor-pointer shrink-0">
                            Choose File
                            <input type="file" accept="application/json" class="hidden" onChange={onUploadProof} />
                        </label>
                    </li>
                </ul>

                <div class="mt-4 flex flex-wrap items-center gap-2">
                    <button
                        class={`btn ${!ready() || loading() ? "opacity-50 cursor-not-allowed" : ""}`}
                        disabled={!ready() || loading()}
                        onClick={runDecrypt}
                        title={!ready() ? "Upload both files to enable" : "Decrypt with your key locally"}
                    >
                        {loading() ? "Decrypting…" : "Decrypt"}
                    </button>
                </div>

                <Show when={err()}>
                    <div class="mt-3 p-3 rounded-xl bg-red-900/30 border border-red-500/30 text-sm">{err()}</div>
                </Show>

                <Show when={out()}>
                    {(o) => (
                        <div class="mt-3 rounded-2xl border border-white/10 bg-white/5 p-4 space-y-1">
                            <div class="text-sm"><b>Tracker ID:</b> {o().recordId}</div>
                            <div class="text-sm"><b>Your vote:</b> Choice {o().candidate + 1}</div>
                        </div>
                    )}
                </Show>
            </section>
        </main>
    );
}

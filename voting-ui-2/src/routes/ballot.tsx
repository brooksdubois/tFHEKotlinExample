// src/routes/ballot.tsx — Modular Forms (Valibot) + seeded Randomize + tRPC/useRxFetch
import {createEffect, createSignal, For, Show} from "solid-js";
import {createForm, Field, setValues, type SubmitHandler, toCustom, valiForm} from "@modular-forms/solid";
import * as v from "valibot";
import {api} from "~/lib/api";
import {useRxFetch} from "~/shared/useRxFetch";
import {useOnline} from "~/shared/Online";
import {TOTAL_CHOICES} from "~/shared/Constants";
import {encryptCandidate, newPersonalKey} from "~/shared/tfhe";

// --------------------------------------------------
// UX helpers
// --------------------------------------------------
const hasValue = (val: unknown) => (typeof val === "number" ? Number.isFinite(val) : !!String(val ?? "").trim());
function LabelReq(props: { for?: string; text: string; filled: boolean }) {
    return (
        <label for={props.for} class="block text-sm font-medium">
            {props.text}
            <span class={`ml-0.5 text-red-600 ${props.filled ? "invisible" : ""}`} aria-hidden={props.filled}>*</span>
        </label>
    );
}

// --------------------------------------------------
// Sample data + deterministic RNG for Randomize
// --------------------------------------------------
const FIRST = ["Ava","Liam","Noah","Olivia","Mia","Ethan","Sophia","Lucas","Isabella","Mason","Amelia","James","Emma","Henry","Harper","Elijah","Charlotte","Benjamin","Evelyn","Jack"] as const;
const LAST  = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez","Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin","Nguyen","Kim","Patel","Singh","Lee"] as const;
const STREETS = ["Maple","Oak","Pine","Cedar","Elm","Birch","Willow","Walnut","Cherry","Ash","River","Hill","Lake","Sunset","Highland"] as const;
const TYPES   = ["St","Ave","Rd","Blvd","Ln","Dr","Ct","Pl","Ter","Way"] as const;
const CITIES  = ["Norwalk","Bridgeport","Detroit","Ann Arbor","Stamford","Grand Rapids","Cleveland","New Haven","Toledo","Rochester"] as const;
const STATES  = ["CT","MI","NY","MA","PA","OH","NJ","IL","RI","NH"] as const;

const mulberry32 = (seed: number) => { let t = seed >>> 0; return () => { t += 0x6D2B79F5; let r = Math.imul(t ^ (t >>> 15), 1 | t); r ^= r + Math.imul(r ^ (r >>> 7), 61 | r); return ((r ^ (r >>> 14)) >>> 0) / 4294967296; }; };
const pick = <T,>(arr: readonly T[], rnd: () => number) => arr[Math.floor(rnd() * arr.length)];
const int = (lo: number, hi: number, rnd: () => number) => Math.floor(rnd() * (hi - lo + 1)) + lo;

// --------------------------------------------------
// Validation schema (Valibot)
// --------------------------------------------------
const BallotSchema = v.object({
    id: v.pipe(v.string(), v.minLength(1, "Required")),
    firstName: v.pipe(v.string(), v.minLength(1, "Required")),
    lastName: v.pipe(v.string(), v.minLength(1, "Required")),
    address1: v.pipe(v.string(), v.minLength(1, "Required")),
    address2: v.optional(v.string()),
    city: v.pipe(v.string(), v.minLength(1, "Required")),
    state: v.pipe(v.string(), v.length(2, "Use 2‑letter code")),
    postal: v.pipe(v.string(), v.minLength(3, "Invalid")),
    age: v.pipe(v.number(), v.minValue(16, "Must be 16+"), v.maxValue(120, "Unrealistic")),
    candidate: v.number("Pick a candidate"),
    receiptCtB64: v.optional(v.string()),
});

const pretty = (obj: unknown) => {
    try { return JSON.stringify(obj, null, 2); } catch { return String(obj); }
};

const copy = (text: string) => navigator.clipboard?.writeText(text).catch(() => {});

const toB64 = (u: Uint8Array) => btoa(String.fromCharCode(...u));

function downloadBin(name: string, bytes: Uint8Array) {
    const blob = new Blob([bytes], { type: "application/octet-stream" });
    const url = URL.createObjectURL(blob);
    const a = Object.assign(document.createElement("a"), { href: url, download: name });
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1500);
}
function downloadJson(name: string, obj: unknown) {
    const blob = new Blob([JSON.stringify(obj, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = Object.assign(document.createElement("a"), { href: url, download: name });
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1500);
}
const b64 = (u: Uint8Array) => btoa(String.fromCharCode(...u));


export type BallotValues = v.InferInput<typeof BallotSchema>;

// --------------------------------------------------
// Page component
// --------------------------------------------------
export default function Ballot() {
    const { online } = useOnline();

    const [form, { Form, Field }] = createForm<BallotValues>({
        initialValues: {
            id: crypto.randomUUID(),
            firstName: "",
            lastName: "",
            address1: "",
            address2: "",
            city: "",
            state: "",
            postal: "",
            age: 18,
            // candidate left undefined until user picks
            receiptCtB64: "",
        },
        validate: valiForm(BallotSchema),
    });

    // Seed for randomize
    const [seed, setSeed] = createSignal<string>("");

    const [trackerId, setTrackerId] = createSignal<string>("");

    // Randomize fills values but remains editable; validates after fill
    const randomize = (alsoSubmit = false) => {
        const s = seed().trim() ? Number(seed()) : undefined;
        const rnd = s != null && Number.isFinite(s) ? mulberry32(s) : Math.random;

        const values: BallotValues = {
            id: crypto.randomUUID(),
            firstName: pick(FIRST, rnd),
            lastName: pick(LAST, rnd),
            address1: `${int(100, 9999, rnd)} ${pick(STREETS, rnd)} ${pick(TYPES, rnd)}`,
            address2: Math.random() < 0.25 ? `Apt ${int(1, 28, rnd)}` : "",
            city: pick(CITIES, rnd),
            state: pick(STATES, rnd),
            postal: String(int(10000, 99999, rnd)).padStart(5, "0"),
            age: int(18, 82, rnd),
            candidate: int(0, TOTAL_CHOICES - 1, rnd),
            receiptCtB64: "",
        } as BallotValues;

        setTrackerId(values.id);
        setValues(form, values, { shouldDirty: false, shouldTouched: false, shouldValidate: true });
        if (alsoSubmit) { setPending(values); }
    };

    // URL params: ?autofill=1[&seed=1337][&autosubmit=1]
    createEffect(() => {
        if (import.meta.env.SSR) return;
        const qs = new URLSearchParams(window.location.search);
        if (qs.has("autofill")) {
            setSeed(qs.get("seed") ?? "");
            randomize(qs.has("autosubmit"));
        }
    });

    // Submission via your tRPC + useRxFetch
    type CastAck = { ok: boolean; candidate: number; recordId: string };

    const [pending, setPending] = createSignal<BallotValues | null>(null);
    const [tfheCks, setTfheCks] = createSignal<any | null>(null);           // client key object (RAM only)
    const [tfheBusy, setTfheBusy] = createSignal(false);
    const [tfheErr, setTfheErr] = createSignal<string | null>(null);

    const [ack, { loading: submitting, error }] =
        useRxFetch<CastAck | null>(
            async () => {
                const values = pending();
                if (!values) throw new Error("Nothing to submit.");

                const fullName = `${values.firstName} ${values.lastName}`.trim();
                const fullAddress = [values.address1, values.address2, `${values.city}, ${values.state} ${values.postal}`]
                    .filter(Boolean)
                    .join("\n");

                return await api.backend.vote.mutate({
                    id: values.id,
                    name: fullName,
                    address: fullAddress,
                    age: values.age,
                    candidate: values.candidate,
                    receiptCtB64: values.receiptCtB64?.trim() || undefined,
                });
            },
            () => pending()?.id,     // refetch key: reruns when pending() changes
            { lazy: true }
        );

    type ReceiptNorm = { id: string; receiptBitsB64?: string[] };
    const [receipt, setReceipt] = createSignal<ReceiptNorm | null>(null);
    const [fetchingReceipt, setFetchingReceipt] = createSignal(false);
    const [receiptError, setReceiptError] = createSignal<string | null>(null);

    async function loadReceipt(id: string) {
        setFetchingReceipt(true);
        setReceiptError(null);
        try {
            const r = await api.backend.receiptById.query({ id });
            setReceipt(r);
        } catch (e) {
            setReceipt(null);
            setReceiptError((e as Error).message || "Receipt fetch failed");
        } finally {
            setFetchingReceipt(false);
        }
    }

    createEffect(() => {
        const id = ack()?.recordId;
        if (!id) return;
        setTrackerId(id);
        // fetch now
        loadReceipt(id);
        // and once more shortly after, to ride out tiny write lag
        setTimeout(() => {
            if (trackerId() === id && (receipt()?.id ?? "") !== id) loadReceipt(id);
        }, 200);
    });

    // Real submit happens here so we get validated values
    const onSubmit: SubmitHandler<BallotValues> = (values) => {
        if (submitting()) return; // re-entrancy guard
        setPending(values);       // key change triggers the fetch
    };

    async function downloadTfheKeyBin() {
        setTfheErr(null);
        if (tfheBusy()) return;
        setTfheBusy(true);
        try {
            const { cks, cksBytes } = await newPersonalKey();
            setTfheCks(cks); // keep in memory so next button can make the proof
            const rid = ack()?.recordId ?? trackerId() ?? "pending";
            downloadBin(`tfhe-key-${rid}.bin`, cksBytes);
        } catch (e) {
            setTfheErr((e as Error).message || "Failed to generate TFHE key");
        } finally {
            setTfheBusy(false);
        }
    }

    async function createTfheProofJson() {
        setTfheErr(null);
        if (tfheBusy()) return;
        if (!ack()) { setTfheErr("Cast a ballot first."); return; }
        const cks = tfheCks();
        if (!cks) { setTfheErr("Generate/download your TFHE key first."); return; }

        setTfheBusy(true);
        try {
            const candidate = ack()!.candidate;
            const { ctBytes } = await encryptCandidate(cks, candidate);
            const proof = { v: 1, recordId: ack()!.recordId, ctB64: toB64(ctBytes) };
            downloadJson(`tfhe-proof-${ack()!.recordId}.json`, proof);
        } catch (e) {
            setTfheErr((e as Error).message || "Failed to create TFHE proof");
        } finally {
            setTfheBusy(false);
        }
    }

    const inSync = () => (receipt()?.id ?? "") === (trackerId() ?? "");
    return (
        <main class="container mx-auto px-4 xl:px-[10%] 2xl:px-[12%] my-8 space-y-6">
            <header class="space-y-1">
                <h2 class="text-2xl font-semibold">Cast Ballot</h2>
                <p class="muted">Modular Forms + Valibot. Required fields show a * and inline error on submit.</p>
                <div class="flex flex-wrap items-center gap-2 mt-2">
                    <Field name="id">
                        {(field, props) => (
                            <input
                                {...props}
                                class="input w-40"
                                placeholder="tracker id"
                                value={field.value || ""}
                                onInput={(e) => setTrackerId(e.currentTarget.value.trim())}
                            />
                        )}
                    </Field>
                    <input class="input w-28" inputmode="numeric" placeholder="seed (opt)" value={seed()}
                           onInput={(e) => setSeed(e.currentTarget.value)} />
                    <button type="button" class="btn" onClick={() => randomize(false)}>Randomize</button>
                    <button type="button" class="btn" onClick={() => randomize(true)}>Randomize + Submit</button>
                </div>
            </header>

            <Form onSubmit={onSubmit} class="grid gap-4">
                {/* Voter fields */}
                <section class="card space-y-3">
                    <div class="grid md:grid-cols-2 gap-3">
                        <Field name="firstName">
                            {(field, props) => (
                                <div>
                                    <LabelReq text="First name" filled={hasValue(field.value)} />
                                    <input {...props} type="text" value={field.value || ""} class="input" />
                                    <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                                </div>
                            )}
                        </Field>
                        <Field name="lastName">
                            {(field, props) => (
                                <div>
                                    <LabelReq text="Last name" filled={hasValue(field.value)} />
                                    <input {...props} type="text" value={field.value || ""} class="input" />
                                    <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                                </div>
                            )}
                        </Field>
                    </div>

                    <Field name="address1">
                        {(field, props) => (
                            <div>
                                <LabelReq text="Address line 1" filled={hasValue(field.value)} />
                                <input {...props} type="text" value={field.value || ""} class="input" />
                                <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                            </div>
                        )}
                    </Field>

                    <Field name="address2">
                        {(field, props) => (
                            <div>
                                <label class="block text-sm font-medium">Address line 2 <span class="muted">(optional)</span></label>
                                <input {...props} type="text" value={field.value || ""} class="input" />
                            </div>
                        )}
                    </Field>

                    <div class="grid md:grid-cols-3 gap-3">
                        <Field name="city">
                            {(field, props) => (
                                <div>
                                    <LabelReq text="City" filled={hasValue(field.value)} />
                                    <input {...props} type="text" value={field.value || ""} class="input" />
                                    <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                                </div>
                            )}
                        </Field>
                        <Field name="state">
                            {(field, props) => (
                                <div>
                                    <LabelReq text="State" filled={hasValue(field.value)} />
                                    <input {...props} type="text" value={field.value || ""} maxLength={2} class="input uppercase" />
                                    <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                                </div>
                            )}
                        </Field>
                        <Field name="postal">
                            {(field, props) => (
                                <div>
                                    <LabelReq text="Postal code" filled={hasValue(field.value)} />
                                    <input {...props} type="text" value={field.value || ""} class="input" />
                                    <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                                </div>
                            )}
                        </Field>
                    </div>

                    <Field name="age" type="number">
                        {(field, props) => (
                            <div>
                                <LabelReq text="Age" filled={hasValue(field.value)} />
                                <input {...props} type="number" value={field.value ?? ""} class="input" />
                                <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                            </div>
                        )}
                    </Field>
                </section>

                {/* Candidate picker */}
                <section class="space-y-2">
                    <Field
                        name="candidate"
                        type="number"
                        transform={toCustom((_, ev) => Number((ev.currentTarget as HTMLInputElement).value), { on: "change" })}
                    >
                        {(field, props) => (
                            <>
                                <div class="text-sm font-medium">
                                    Pick one candidate
                                    <span class={`ml-0.5 text-red-600 ${hasValue(field.value) ? "invisible" : ""}`} aria-hidden={hasValue(field.value)}>*</span>
                                </div>
                                <div class="grid gap-3" style={{ "grid-template-columns": `repeat(${TOTAL_CHOICES}, minmax(0, 1fr))` }}>
                                    <For each={Array.from({ length: TOTAL_CHOICES }, (_, i) => i)}>
                                        {(i) => {
                                            const id = `choice-${i}`;
                                            const selected = () => field.value === i;
                                            return (
                                                <label for={id} class="card cursor-pointer select-none transition" classList={{ "ring-1 ring-black/20": selected() }}>
                                                    <div class="flex items-center gap-3">
                                                        <input {...props} id={id} type="radio" value={i} checked={selected()} class="peer sr-only" />
                                                        <span class="h-5 w-5 rounded-full border border-black/30 grid place-items-center" classList={{ "border-black": selected() }} aria-hidden="true">
                              <span class={`h-3 w-3 rounded-full ${selected() ? "bg-black" : ""}`} />
                            </span>
                                                        <span class="font-medium">Choice {i + 1}</span>
                                                    </div>
                                                </label>
                                            );
                                        }}
                                    </For>
                                </div>
                                <Show when={field.error}><p class="text-xs text-red-600 mt-1">{field.error}</p></Show>
                            </>
                        )}
                    </Field>
                </section>

                {/* Submit */}
                <div class="flex items-center gap-3">
                    <button class="btn" type="submit" disabled={submitting() || !online()}>
                        {submitting() ? "Submitting…" : "Submit Ballot"}
                    </button>
                    <Show when={!online()}><span class="muted text-sm">Backend offline</span></Show>
                </div>
            </Form>
            <Show when={error()}>
                <div class="p-3 rounded-xl bg-red-900/30 border border-red-500/30 text-sm">
                    {String(error())}
                </div>
            </Show>

            <Show when={ack()}>
                {(r) => (
                    <section class="card space-y-2">
                        <div class="text-sm">Vote accepted.</div>
                        <div><b>Tracker ID:</b> <span class="break-all">{r().recordId}</span></div>
                        <div><b>Candidate:</b> {r().candidate}</div>
                        <div class="text-xs opacity-70">
                            Save your tracker ID. If you included <code>receiptCtB64</code>, you can later fetch it via
                            <code> /receipt/{r().recordId}</code>.
                        </div>

                        <div class="flex flex-wrap gap-2 pt-2">
                            <button class="btn" type="button" disabled={tfheBusy()} onClick={downloadTfheKeyBin}>
                                {tfheBusy() ? "Working…" : "Download TFHE Key (.bin)"}
                            </button>
                            <button class="btn" type="button" disabled={tfheBusy() || !tfheCks()} onClick={createTfheProofJson}>
                                Create TFHE Proof (.json)
                            </button>
                            <Show when={!tfheCks()}>
                                <span class="text-xs opacity-70">← Generate key first</span>
                            </Show>
                        </div>

                        <Show when={tfheErr()}>
                            <div class="p-2 rounded border border-red-500/30 bg-red-900/20 text-xs">{tfheErr()}</div>
                        </Show>
                    </section>
                )}
            </Show>
            {/* Receipt panel */}
            <section class="card space-y-3">
                <div class="flex items-center justify-between gap-3">
                    <div class="font-medium">Stored Receipt</div>
                    <div class="flex items-center gap-2">
                        <button
                            class="btn"
                            disabled={fetchingReceipt() || !trackerId()}
                            onClick={() => trackerId() && loadReceipt(trackerId()!)}
                        >
                            {fetchingReceipt() ? "Fetching…" : "Fetch from server"}
                        </button>
                        <Show when={inSync()}><span class="text-xs px-2 py-0.5 rounded bg-green-600/10 text-green-700">Fresh</span></Show>
                        <Show when={!inSync() && receipt()}><span class="text-xs px-2 py-0.5 rounded bg-amber-500/15 text-amber-700">Stale — fetch</span></Show>
                    </div>
                </div>

                <Show when={!trackerId()}>
                    <p class="text-sm opacity-70">No tracker ID yet. Cast a ballot first.</p>
                </Show>

                <Show when={receiptError()}>
                    <div class="p-3 rounded-xl bg-red-900/30 border border-red-500/30 text-sm">
                        {String(receiptError())}
                    </div>
                </Show>
                <Show when={receipt()}>
                    {(r) => {
                        const rec = r()!;
                        const empty = !(rec.receiptBitsB64?.length);
                        return (
                            <>
                                <div class="text-sm"><b>Tracker ID:</b> <span class="break-all">{trackerId()}</span></div>
                                <Show when={!empty} fallback={
                                    <p class="text-sm opacity-70">
                                        No server-stored receipt for this tracker. (You can still proceed if you kept your local client key.)
                                    </p>
                                }>
                                    <div class="flex flex-wrap gap-2">
                                        <button
                                            class="btn"
                                            disabled={!inSync() || fetchingReceipt()}
                                            onClick={() => {
                                                const rcv = receipt()!;
                                                downloadJson(`receipt-${rcv.id}.json`, {
                                                    id: rcv.id,
                                                    receiptBitsB64: rcv.receiptBitsB64 ?? [],
                                                });
                                            }}
                                        >
                                            Download Receipt (.json)
                                        </button>
                                        <button class="btn"
                                                onClick={() => copy((rec.receiptBitsB64 ?? []).join("\n"))}>
                                            Copy base64
                                        </button>
                                    </div>
                                    <details class="mt-2">
                                        <summary class="cursor-pointer opacity-70">Show receipt base64</summary>
                                        <div class="max-w-full overflow-x-auto rounded bg-black/5 p-3">
                                             <pre class="inline-block whitespace-pre font-mono text-xs leading-relaxed">
                                                {(rec.receiptBitsB64 ?? []).join("\n")}
                                            </pre>
                                        </div>
                                    </details>
                                </Show>
                            </>
                        );
                    }}
                </Show>
            </section>
        </main>
    );
}

import { api } from "~/lib/api";
import { useRxFetch } from "~/shared/useRxFetch";
import {Show, createSignal, onMount, createMemo} from "solid-js";

type LookupIncluded = {
    status: "INCLUDED";
    record: { id: string; timestamp: number; commitment: string };
};
type LookupNotFound = { status: "NOT_FOUND" };

export default function Verify() {

    const [lookupId, setLookupId] = createSignal("");
    type LookupResp = { status: "INCLUDED"; record: { id: string; timestamp: number; commitment: string } }
        | { status: "NOT_FOUND" };

    const [lookup, { loading: lookupLoading, error: lookupError, refetch: runLookup }] =
        useRxFetch<LookupResp | null>(
            async () => {
                const id = lookupId().trim();
                if (!id) return null;              // ← skip on first render / empty
                return api.backend.lookup.query({ trackerId: id });
            },
            () => !!lookupId().trim(),           // ← optional: only “eligible” when there’s an ID
            { lazy: true }
        );

    const included = createMemo<LookupIncluded | null>(() => {
        const r = lookup();
        return r && r.status === "INCLUDED" ? r : null;
    });

    return (
        <div class="mx-auto max-w-xl p-6 space-y-5">
            <div class="space-y-1">
                <div class="text-2xl font-semibold">Lookup</div>
            </div>

            <section class="space-y-2">
                <h3 class="text-lg font-medium">Lookup by Tracker ID</h3>
                <div class="flex gap-2">
                    <input
                        class="input"
                        placeholder="paste tracker id"
                        value={lookupId()}
                        onInput={e => setLookupId(e.currentTarget.value)}
                    />
                    <button
                        class="btn"
                        disabled={!lookupId().trim() || lookupLoading()}
                        onClick={() => runLookup()}
                    >
                        {lookupLoading() ? "Checking…" : "Lookup"}
                    </button>
                </div>

                <Show when={lookupError()}>
                    <p class="text-sm text-red-500">{String(lookupError())}</p>
                </Show>

                <Show when={included()} fallback={<div class="card mt-2 text-sm">Not found.</div>} keyed>
                    {(r) => (
                        <div class="card mt-2 text-sm">
                            <div><b>Status:</b> INCLUDED</div>
                            <div class="break-all"><b>Tracker ID:</b> {r.record.id}</div>
                            <div><b>Timestamp:</b> {new Date(r.record.timestamp * 1000).toLocaleString()}</div>
                            <div class="break-all"><b>Commitment:</b> {r.record.commitment}</div>
                        </div>
                    )}
                </Show>
            </section>
        </div>
    );
}

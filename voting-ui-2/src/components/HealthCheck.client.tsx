// src/components/HealthCheck.client.tsx (or your existing path)
import { Show, createEffect } from "solid-js";
import { api } from "~/lib/api";
import { useRxFetch } from "~/shared/useRxFetch";
import { useOnline } from "~/shared/Online";

export default function HealthCheck() {
    const { setOnline } = useOnline();

    const [health, { loading, refetch, error }] = useRxFetch<string | null>(async () => {
        const res = await api.backend.health.query();
        return res.ok === true ? "Health Check Succeeded" : null;
    });

    // Only update online after the first *client* result (loading is false)
    createEffect(() => {
        if (loading()) return;            // wait for first attempt
        const h = health();               // undefined (no run yet), string, or null
        if (h !== undefined) setOnline(Boolean(h));
    });

    return (
        <section>
            <Show when={!loading()} fallback={<p>Checking…</p>}>
                <Show
                    when={health()}
                    fallback={
                        <p class="text-red-600">
                            Backend offline. <button class="btn" onClick={refetch}>Retry</button>
                        </p>
                    }
                >
                    <pre class="bg-black/5 p-2 rounded">{health()}</pre>
                </Show>
            </Show>
        </section>
    );
}

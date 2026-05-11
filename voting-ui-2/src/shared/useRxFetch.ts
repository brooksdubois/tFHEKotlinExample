// shared/useRxFetch.ts
import { createEffect, createSignal, onCleanup, onMount } from "solid-js";
import { from, isObservable, type Observable, type Subscription, defer } from "rxjs";

type Factory<T> = () => Observable<T> | Promise<T> | T;

export function useRxFetch<T>(
    factory: Factory<T>,
    deps?: () => unknown,
    opts?: { lazy?: boolean }
) {
    const [data, setData] = createSignal<T | undefined>();
    const [error, setError] = createSignal<unknown>();
    const [loading, setLoading] = createSignal(false);

    let sub: Subscription | null = null;

    const to$ = (f: Factory<T>): Observable<T> => {
        const v = f();
        return isObservable(v) ? v : from(Promise.resolve(v));
    };

    const run = () => {
        sub?.unsubscribe();
        setLoading(true);
        setError(() => undefined);
        sub = defer(() => to$(factory)).subscribe({
            next: (v) => {
                setData(() => v as T);     // use updater form (handles function T)
                setLoading(false);
            },
            error: (e) => {
                setError(() => e);
                setLoading(false);
            },
        });
    };

    // IMPORTANT: do not execute on SSR — only wire up on the client
    if (!import.meta.env.SSR) {
        if (deps) createEffect(() => { deps(); run(); });
        else onMount(run);
    }

    const start = () => {
        if (deps) createEffect(() => { deps(); run(); });
        else onMount(run);
    };

    if (!import.meta.env.SSR && !opts?.lazy) start();

    onCleanup(() => sub?.unsubscribe());
    return [data, { loading, error, refetch: run }] as const;
}

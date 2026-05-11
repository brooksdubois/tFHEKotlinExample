// src/lib/gateLink.ts
import type { TRPCLink } from "@trpc/client";
import { observable } from "@trpc/server/observable";
import type { AppRouter } from "~/server/api/root";

export const gateLink: TRPCLink<AppRouter> = () =>
    ({next, op}) =>
        observable((observer) => {
            const sub = next(op).subscribe(observer);
            return () => sub.unsubscribe?.();
        });

import type { APIEvent } from "@solidjs/start/server";
import { fetchRequestHandler } from "@trpc/server/adapters/fetch";
import { appRouter } from "~/server/api/root";

const handler = (event: APIEvent) =>
  fetchRequestHandler({
    endpoint: "/api/trpc",
    req: event.request,
    router: appRouter,
    createContext:  () => event,
    onError({ path, error }) {
      console.error("[tRPC]", path, error);
    }
  });

export const GET = handler;
export const POST = handler;

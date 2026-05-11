import {
  createTRPCProxyClient,
  httpBatchLink,
  loggerLink,
} from '@trpc/client';
import { AppRouter } from "~/server/api/root";
import {gateLink} from "~/lib/gateLink";

const getBaseUrl = () => {
  if (typeof window !== "undefined") return "";
  if (process.env.NODE_ENV === "production") return "https://example.com";
  return `http://localhost:${process.env.PORT ?? 3000}`;
};

export const api = createTRPCProxyClient<AppRouter>({
  links: [
      loggerLink(),
      gateLink,
      httpBatchLink({ url: `${getBaseUrl()}/api/trpc` })
  ],
});

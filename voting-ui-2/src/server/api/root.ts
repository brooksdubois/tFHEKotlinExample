import { createTRPCRouter } from "./utils";
import { ktorTfhe } from "./routers/ktorTfhe";

export const appRouter = createTRPCRouter({
    backend: ktorTfhe,
});

export type AppRouter = typeof appRouter;
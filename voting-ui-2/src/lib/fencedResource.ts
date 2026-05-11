// src/lib/fencedResource.ts
import { createResource } from "solid-js";
import { isServer } from "solid-js/web";

export const createFencedResource = <T>(deps: () => boolean, fetcher: () => Promise<T>) => createResource(
    () => (!isServer && deps()) ? 1 : null,
    () => fetcher(),
);

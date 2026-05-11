import { createContext, useContext, type Accessor } from "solid-js";
import type { Setter } from "solid-js";

type OnlineCtx = { online: Accessor<boolean>; setOnline: Setter<boolean> };
export const OnlineContext = createContext<OnlineCtx>();
export const useOnline = () => useContext(OnlineContext)!;

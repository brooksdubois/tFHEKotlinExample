import { MetaProvider, Title } from "@solidjs/meta";
import {A, Router} from "@solidjs/router";
import { FileRoutes } from "@solidjs/start/router";
import {Suspense, ErrorBoundary } from "solid-js";
import { createSignal } from "solid-js";
import { OnlineContext } from "~/shared/Online";
import HealthCheck from "~/components/HealthCheck.client";
import "./app.css";

export default function App() {
    const [online, setOnline] = createSignal(false);

    return (
        <Router
            root={(props) => (
                <OnlineContext.Provider value={{ online, setOnline }}>
                    <MetaProvider>
                        <Title>SolidStart - Basic</Title>
                        <nav class="space-x-3">
                            <A href="/">Home</A>
                            <A href="/ballot">Cast Ballot</A>
                            <A href="/lookup">Lookup</A>
                            <A href="/verify">Verify</A>
                            <A href="/mpc">Tally</A>
                        </nav>
                        <ErrorBoundary fallback={<div class="p-3">Offline: <HealthCheck/></div>}>
                            <Suspense fallback={<div class="p-3">Loading…</div>}>
                                {/*{ ssrHealth() ? "Online" : "Offline" }*/}
                                <HealthCheck/>
                                {props.children}
                            </Suspense>
                        </ErrorBoundary>
                    </MetaProvider>
                </OnlineContext.Provider>
            )}
        >
            <FileRoutes/>
        </Router>
    );
}

// app.config.ts
import { defineConfig } from "@solidjs/start/config";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
    vite: {
        optimizeDeps: { exclude: ["tfhe"] },   // don't prebundle the wasm module
        ssr: { noExternal: ["tfhe"] },         // keep ESM shape for client bundle
        plugins: [
            tailwindcss(),
            {
                name: "wasm-mime",
                configureServer(server) {
                    server.middlewares.use((req, res, next) => {
                        if (req.url?.endsWith(".wasm")) res.setHeader("Content-Type", "application/wasm");
                        next();
                    });
                },
                configurePreviewServer(server) {
                    server.middlewares.use((req, res, next) => {
                        if (req.url?.endsWith(".wasm")) res.setHeader("Content-Type", "application/wasm");
                        next();
                    });
                },
            },
        ],
    },
});
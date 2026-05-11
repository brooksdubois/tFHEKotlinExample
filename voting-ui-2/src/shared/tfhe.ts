// src/shared/tfhe.ts
let mod: any;

export async function tfhe() {
    if (mod) return mod;
    if (import.meta.env.SSR) throw new Error("TFHE runs in the browser only");

    const { default: init, ...m } = await import("tfhe");

    // Absolute path from /public. Note the underscore file name.
    const wasmUrl = "/vendor/tfhe_bg.wasm";

    // New recommended signature: pass an options object
    await init({ module_or_path: wasmUrl });

    m.init_panic_hook?.();
    mod = m;
    return mod;
}

// the rest of your helpers can stay as you had them
export async function newPersonalKey() {
    const { Shortint, ShortintParameters, ShortintParametersName } = await tfhe();
    const params = new ShortintParameters(
        ShortintParametersName.V1_3_PARAM_MESSAGE_2_CARRY_2_COMPACT_PK_PBS_KS_GAUSSIAN_2M64
    );
    const cks = Shortint.new_client_key(params);
    const cksBytes: Uint8Array = Shortint.serialize_client_key(cks);
    return { cks, cksBytes };
}

export async function encryptCandidate(cks: any, value: number) {
    const { Shortint } = await tfhe();
    const ct = Shortint.encrypt(cks, BigInt(value));
    const ctBytes: Uint8Array = Shortint.serialize_ciphertext(ct);
    return { ct, ctBytes };
}

export async function decryptCandidate(cksBytes: Uint8Array, ctBytes: Uint8Array): Promise<number> {
    const { Shortint } = await tfhe();
    const cks = Shortint.deserialize_client_key(cksBytes);
    const ct  = Shortint.deserialize_ciphertext(ctBytes);
    return Number(Shortint.decrypt(cks, ct));
}

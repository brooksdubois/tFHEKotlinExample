package jniNative;

public class TfheBridgeJNI {
    
    public static native void tfhe_free_keypair(long keyPtr);

    // Encryption / Decryption
    public static native long tfhe_encrypt_with(long keyPtr, byte value);
    public static native byte tfhe_decrypt_with(long keyPtr, long ctPtr);

    // Logic gates
    public static native long tfhe_and_with(long keyPtr, long aPtr, long bPtr);
    public static native long tfhe_or_with(long keyPtr, long aPtr, long bPtr);
    public static native long tfhe_xor_with(long keyPtr, long aPtr, long bPtr);
    public static native long tfhe_not_with(long keyPtr, long ctPtr);

    // Serialization
    public static native byte[] serialize_ciphertext(long ctPtr);
    public static native byte tfhe_decrypt_serialized_with(long keyPtr, byte[] serialized);

    // Debug
    public static native long echo_ptr(long ptr);

    public static native byte[] export_client_key(long keyPtr);
    public static native byte[] export_cloud_key(long keyPtr);

    public static native long import_client_key(byte[] serialized);
    public static native long import_cloud_key(byte[] serialized);
    public static native long deserialize_ciphertext(byte[] data);

    // === Integer (u16) ===
    public static native long  tfhe_int_generate_keys();
    public static native long  tfhe_int_encryptU16(long keyPtr, int value);
    public static native long  tfhe_int_add(long keyPtr, long aPtr, long bPtr);
    public static native int   tfhe_int_decryptU16(long keyPtr, long ctPtr);
    public static native byte[] tfhe_int_serialize(long ctPtr);
    public static native long   tfhe_int_deserialize(byte[] data);

    // compressed server key I/O
    public static native byte[] tfhe_int_exportCompressedServerKey(long keyPtr);
    public static native long   tfhe_int_importCompressedServerKey(byte[] data);

    // === Integer (u16) server-only context (verifier) ===
    public static native long   tfhe_int_serverCtxFromCompressed(byte[] compressedServerKey);
    public static native long   tfhe_int_addWithServer(long serverCtxPtr, long aPtr, long bPtr);

    // === Integer (u16) frees (optional but recommended) ===
    public static native void   tfhe_int_freeKeypair(long keyPtr);
    public static native void   tfhe_int_freeCiphertext(long ctPtr);
    public static native void   tfhe_int_freeServerCtx(long serverCtxPtr);

    static {
        boolean loaded = false;

        // 0) Explicit overrides first
        try {
            String explicit = System.getProperty("tfhe.bridge.path");
            if (explicit == null || explicit.isEmpty()) {
                explicit = System.getenv("TFHE_BRIDGE_PATH");
            }
            if (explicit != null && !explicit.isEmpty()) {
                System.load(new java.io.File(explicit).getAbsolutePath());
                loaded = true;
            }
        } catch (Throwable ignore) { /* fall through */ }

        // 1) Try standard lookup if not already loaded
        if (!loaded) {
            try {
                System.loadLibrary("tfhe_bridge");
                loaded = true;
            } catch (Throwable ignore) { /* fall through */ }
        }

        // 2) Search common dev locations, preferring <repo>/libs
        if (!loaded) {
            String os = System.getProperty("os.name", "").toLowerCase();
            String libName = os.contains("mac") ? "libtfhe_bridge.dylib"
                    : os.contains("win") ? "tfhe_bridge.dll"
                    : "libtfhe_bridge.so";

            java.io.File userDir = new java.io.File(System.getProperty("user.dir"));

            java.io.File[] dirs = new java.io.File[] {
                    new java.io.File(userDir, "libs"),
                    new java.io.File(userDir + "/kvm-blockchain", "libs"),   // ./libs (your case
                    userDir,                                         // .
                    userDir.getParentFile() == null ? null : new java.io.File(userDir.getParentFile(), "libs"),     // ../libs
                    userDir.getParentFile() == null || userDir.getParentFile().getParentFile() == null
                            ? null : new java.io.File(userDir.getParentFile().getParentFile(), "libs")                 // ../../libs
            };

            for (java.io.File d : dirs) {
                if (loaded || d == null) continue;
                java.io.File candidate = new java.io.File(d, libName);
                if (candidate.exists()) {
                    System.load(candidate.getAbsolutePath());
                    loaded = true;
                }
            }
        }

        if (!loaded) {
            throw new UnsatisfiedLinkError(
                    "tfhe_bridge native library not found. " +
                            "Set -Dtfhe.bridge.path=/abs/path/to/libtfhe_bridge.* or TFHE_BRIDGE_PATH, " +
                            "or place the lib under <repo>/libs/"
            );
        }
    }
}


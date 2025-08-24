package jniNative;

public class TfheBridgeJNI {
    public static native long   tfhe_int_generate_keys();
    public static native long   tfhe_int_encryptU16(long keyPtr, int value);
    public static native long   tfhe_int_add(long keyPtr, long aPtr, long bPtr);
    public static native int    tfhe_int_decryptU16(long keyPtr, long ctPtr);
    public static native byte[] tfhe_int_serialize(long ctPtr);
    public static native long   tfhe_int_deserialize(byte[] data);
    public static native byte[] tfhe_int_exportCompressedServerKey(long keyPtr);
    public static native long   tfhe_int_importCompressedServerKey(byte[] data);
    public static native long   tfhe_int_serverCtxFromCompressed(byte[] data);
    public static native long   tfhe_int_addWithServer(long srvPtr, long aPtr, long bPtr);
    public static native void   tfhe_int_freeKeypair(long keyPtr);
    public static native void   tfhe_int_freeCiphertext(long ctPtr);
    public static native void   tfhe_int_freeServerCtx(long srvPtr);

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


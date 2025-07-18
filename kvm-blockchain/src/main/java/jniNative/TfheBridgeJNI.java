package jniNative;

public class TfheBridgeJNI {

    static {
        System.loadLibrary("tfhe_bridge");
    }

    // Keygen
    public static native void tfhe_init_keys();

    // Encryption from byte array (preferred)
    public static native long tfhe_encrypt_byte(byte[] input);

    // Logic operations
    public static native long tfhe_and(long a, long b);
    public static native long tfhe_or(long a, long b);
    public static native long tfhe_not(long ptr);
    public static native long tfhe_xor(long a, long b);

    // Decryption
    public static native boolean tfhe_decrypt_bit(long ptr);

    // Debugging
    public static native long echo_ptr(long ptr);

    public static native byte[] serialize_ciphertext(long ptr);

    public static native boolean tfhe_decrypt_serialized(byte[] input);

    public static native byte[] export_client_key();
    public static native void import_client_key(byte[] keyBytes);

    public static native byte[] export_cloud_key();
    public static native void import_cloud_key(byte[] key);

}

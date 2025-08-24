use tfhe::boolean::prelude::*;
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jboolean, jlong, jbyte, jint};
use tfhe::{ConfigBuilder, generate_keys, with_server_key_as_context, FheUint16};
use tfhe::{CompressedServerKey};
use tfhe::prelude::{FheDecrypt, FheEncrypt};
// compressed at rest

#[repr(C)]
pub struct Keypair {
    pub client: ClientKey,
    pub server: ServerKey,
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1generate_1keys(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let (client, server) = gen_keys();
    let kp = Box::new(Keypair { client, server });
    Box::into_raw(kp) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1free_1keypair(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe { let _ = Box::from_raw(ptr as *mut Keypair); };
    }
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1encrypt_1with(
    _env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
    input: jbyte,
) -> jlong {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let bit = input != 0;
    let ct = key.client.encrypt(bit);
    Box::into_raw(Box::new(ct)) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1decrypt_1with(
    _env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
    ct_ptr: jlong,
) -> jbyte {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let ct = unsafe { &*(ct_ptr as *mut Ciphertext) };
    key.client.decrypt(ct) as jbyte
}

macro_rules! binary_op {
    ($name:ident, $method:ident) => {
        #[no_mangle]
        pub extern "C" fn $name(
            _env: JNIEnv,
            _class: JClass,
            key_ptr: jlong,
            a_ptr: jlong,
            b_ptr: jlong,
        ) -> jlong {
            let key = unsafe { &*(key_ptr as *mut Keypair) };
            let a = unsafe { &*(a_ptr as *mut Ciphertext) };
            let b = unsafe { &*(b_ptr as *mut Ciphertext) };
            let result = key.server.$method(a, b);
            Box::into_raw(Box::new(result)) as jlong
        }
    };
}

binary_op!(Java_jniNative_TfheBridgeJNI_tfhe_1and_1with, and);
binary_op!(Java_jniNative_TfheBridgeJNI_tfhe_1or_1with, or);
binary_op!(Java_jniNative_TfheBridgeJNI_tfhe_1xor_1with, xor);

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1not_1with(
    _env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
    ct_ptr: jlong,
) -> jlong {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let ct = unsafe { &*(ct_ptr as *mut Ciphertext) };
    let result = key.server.not(ct);
    Box::into_raw(Box::new(result)) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_serialize_1ciphertext(
    env: JNIEnv,
    _class: JClass,
    ct_ptr: jlong,
) -> jbyteArray {
    let ct = unsafe { &*(ct_ptr as *mut Ciphertext) };
    let bytes = bincode::serialize(ct).expect("Serialization failed");
    env.byte_array_from_slice(&bytes).expect("Failed to create jbyteArray").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1decrypt_1serialized_1with(
    env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
    input: JByteArray,
) -> jboolean {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let ct: Ciphertext = bincode::deserialize(&bytes).expect("Deserialization failed");
    key.client.decrypt(&ct) as u8
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_echo_1ptr(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jlong {
    println!("🔁 echo_ptr received = {}", ptr);
    ptr
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_export_1client_1key(
    env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
) -> jbyteArray {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let bytes = bincode::serialize(&key.client).expect("ClientKey serialization failed");
    env.byte_array_from_slice(&bytes).expect("jbyteArray conversion failed").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_export_1cloud_1key(
    env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
) -> jbyteArray {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let bytes = bincode::serialize(&key.server).expect("ServerKey serialization failed");
    env.byte_array_from_slice(&bytes).expect("jbyteArray conversion failed").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_import_1client_1key(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let client_key: ClientKey = bincode::deserialize(&bytes).expect("Failed to deserialize ClientKey");

    // We provide a dummy ServerKey so it can be used for re-encryption or upgrades later
    let server_key = ServerKey::new(&client_key);

    let kp = Box::new(Keypair { client: client_key, server: server_key });
    Box::into_raw(kp) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_import_1cloud_1key(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let server_key: ServerKey = bincode::deserialize(&bytes).expect("Failed to deserialize ServerKey");

    // Generate a dummy client key by calling gen_keys()
    let (client_key, _) = gen_keys();

    let kp = Box::new(Keypair {
        client: client_key,
        server: server_key,
    });

    Box::into_raw(kp) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_deserialize_1ciphertext(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let ct: Ciphertext = bincode::deserialize(&bytes).expect("Failed to deserialize Ciphertext");
    Box::into_raw(Box::new(ct)) as jlong
}

#[inline]
fn jlong_as_ref<'a, T>(ptr: jlong, what: &str) -> &'a T {
    if ptr == 0 { panic!("{}", what); }
    // SAFETY: same justification; we only take a temporary shared borrow.
    unsafe { &*(ptr as *const T) }
}

#[repr(C)]
pub struct IntKeypair {
    client: tfhe::ClientKey, // integer API re-export
    server: tfhe::ServerKey,
}

// keygen (u16-only)
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1generate_1keys(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    // Keep the config as small as possible (u16 default params)
    let config = ConfigBuilder::default().build();
    let (client, server) = generate_keys(config);
    Box::into_raw(Box::new(IntKeypair { client, server })) as jlong
}

// encrypt u16 with *client* key
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1encryptU16(
    _env: JNIEnv, _cls: JClass, kp_ptr: jlong, value: jint,
) -> jlong {
    let kp = jlong_as_ref::<IntKeypair>(kp_ptr, "null IntKeypair");
    let ct = FheUint16::encrypt(value as u16, &kp.client);
    Box::into_raw(Box::new(ct)) as jlong
}

// homomorphic add: c = a + b  (uses server key *without* global set)
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1add(
    _env: JNIEnv, _cls: JClass, kp_ptr: jlong, a_ptr: jlong, b_ptr: jlong,
) -> jlong {
    let kp = jlong_as_ref::<IntKeypair>(kp_ptr, "null IntKeypair");
    let a  = jlong_as_ref::<FheUint16>(a_ptr,  "null ciphertext a");
    let b  = jlong_as_ref::<FheUint16>(b_ptr,  "null ciphertext b");
    let out = with_server_key_as_context(kp.server.clone(), || a + b);
    Box::into_raw(Box::new(out)) as jlong
}

// decrypt u16
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1decryptU16(
    _env: JNIEnv, _cls: JClass, kp_ptr: jlong, ct_ptr: jlong,
) -> jint {
    let kp = jlong_as_ref::<IntKeypair>(kp_ptr, "null IntKeypair");
    let ct = jlong_as_ref::<FheUint16>(ct_ptr, "null ciphertext");
    let clear: u16 = ct.decrypt(&kp.client);
    clear as jint
}

// serialize/deserialize ciphertexts (still via bincode)
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1serialize(
    env: JNIEnv, _cls: JClass, ct_ptr: jlong,
) -> jbyteArray {
    let ct = jlong_as_ref::<FheUint16>(ct_ptr, "null ciphertext");
    let bytes = bincode::serialize(ct).expect("serialize ct");
    env.byte_array_from_slice(&bytes).unwrap().as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1deserialize(
    env: JNIEnv, _cls: JClass, input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).unwrap();
    let ct: FheUint16 = bincode::deserialize(&bytes).expect("deserialize ct");
    Box::into_raw(Box::new(ct)) as jlong
}

// --- compressed server key export/import (to avoid GB-sized blobs) ---

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1exportCompressedServerKey(
    env: JNIEnv, _cls: JClass, kp_ptr: jlong,
) -> jbyteArray {
    let kp = jlong_as_ref::<IntKeypair>(kp_ptr, "null IntKeypair");
    let csk = CompressedServerKey::new(&kp.client); // build from client key
    let bytes = bincode::serialize(&csk).expect("serialize csk");
    env.byte_array_from_slice(&bytes).unwrap().as_raw()
}
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1importCompressedServerKey(
    env: JNIEnv, _cls: JClass, input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).unwrap();
    let csk: CompressedServerKey = bincode::deserialize(&bytes).expect("deserialize csk");
    let server = csk.decompress();

    // NOTE: This client key will NOT match `server` and cannot decrypt.
    // It's only here because IntKeypair currently requires a client.
    let config = ConfigBuilder::default().build();
    let client = tfhe::ClientKey::generate(config);

    Box::into_raw(Box::new(IntKeypair { client, server })) as jlong
}




use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jlong, jint};
use tfhe::{ConfigBuilder, generate_keys, with_server_key_as_context, FheUint16, ClientKey};
use tfhe::{CompressedServerKey};
use tfhe::prelude::{FheDecrypt, FheEncrypt};
use tfhe::ServerKey;

#[repr(C)]
pub struct Keypair {
    pub client: ClientKey,
    pub server: ServerKey,
}

// Just under IntKeypair, add a server-only holder:
#[repr(C)]
pub struct IntServerCtx {
    server: tfhe::ServerKey,
}

// Build server-only ctx from a compressed server key (bytes).
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1serverCtxFromCompressed(
    env: JNIEnv, _cls: JClass, input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).expect("invalid bytes");
    let csk: CompressedServerKey = bincode::deserialize(&bytes).expect("deserialize csk");
    let server = csk.decompress();
    Box::into_raw(Box::new(IntServerCtx { server })) as jlong
}

// Homomorphic add using server-only ctx (no global set).
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1addWithServer(
    _env: JNIEnv, _cls: JClass, srv_ptr: jlong, a_ptr: jlong, b_ptr: jlong,
) -> jlong {
    let srv = jlong_as_ref::<IntServerCtx>(srv_ptr, "null server ctx");
    let a   = jlong_as_ref::<FheUint16>(a_ptr, "null a");
    let b   = jlong_as_ref::<FheUint16>(b_ptr, "null b");
    let out = with_server_key_as_context(srv.server.clone(), || a + b);
    Box::into_raw(Box::new(out)) as jlong
}

// Optional but recommended frees (mirror your Java JNI signatures):
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1freeKeypair(
    _env: JNIEnv, _cls: JClass, kp_ptr: jlong,
) {
    if kp_ptr != 0 { unsafe { drop(Box::from_raw(kp_ptr as *mut IntKeypair)); } }
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1freeCiphertext(
    _env: JNIEnv, _cls: JClass, ct_ptr: jlong,
) {
    if ct_ptr != 0 { unsafe { drop(Box::from_raw(ct_ptr as *mut FheUint16)); } }
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1freeServerCtx(
    _env: JNIEnv, _cls: JClass, srv_ptr: jlong,
) {
    if srv_ptr != 0 { unsafe { drop(Box::from_raw(srv_ptr as *mut IntServerCtx)); } }
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
pub extern "C" fn Java_jniNative_TfheBridgeJNI_export_1cloud_1key(
    env: JNIEnv,
    _class: JClass,
    key_ptr: jlong,
) -> jbyteArray {
    let key = unsafe { &*(key_ptr as *mut Keypair) };
    let bytes = bincode::serialize(&key.server).expect("ServerKey serialization failed");
    env.byte_array_from_slice(&bytes).expect("jbyteArray conversion failed").as_raw()
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

// Export the election ClientKey (dev-only)
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1exportClientKey(
    env: JNIEnv, _cls: JClass, kp_ptr: jlong,
) -> jbyteArray {
    let kp = unsafe { jlong_as_ref::<IntKeypair>(kp_ptr, "null IntKeypair") };
    let bytes = bincode::serialize(&kp.client).expect("serialize client key");
    env.byte_array_from_slice(&bytes).unwrap().as_raw()
}

// Import a ClientKey and pair it with a matching ServerKey (derived via CompressedServerKey)
#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1importClientKey(
    env: JNIEnv, _cls: JClass, input: JByteArray,
) -> jlong {
    let bytes = env.convert_byte_array(input).unwrap();
    let client: ClientKey = bincode::deserialize(&bytes).expect("deserialize client key");
    let server = CompressedServerKey::new(&client).decompress();
    Box::into_raw(Box::new(IntKeypair { client, server })) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1int_1addClearWithServer(
    _env: jni::JNIEnv, _cls: jni::objects::JClass, srv_ptr: jni::sys::jlong,
    ct_ptr: jni::sys::jlong, clear: jni::sys::jint,
) -> jni::sys::jlong {
    let srv = jlong_as_ref::<IntServerCtx>(srv_ptr, "null server ctx");
    let ct  = jlong_as_ref::<tfhe::FheUint16>(ct_ptr, "null ct");
    let out = tfhe::with_server_key_as_context(srv.server.clone(), || ct + (clear as u16));
    Box::into_raw(Box::new(out)) as jni::sys::jlong
}




use tfhe::boolean::prelude::*;
use std::sync::OnceLock;
use jni::JNIEnv;
use jni::objects::{JClass, JByteArray};
use jni::sys::{jbyteArray, jlong, jboolean};

static CLIENT_KEY: OnceLock<ClientKey> = OnceLock::new();
static SERVER_KEY: OnceLock<ServerKey> = OnceLock::new();
static CLOUD_KEY: OnceLock<ServerKey> = OnceLock::new(); // for exportable cloud key

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1init_1keys(
    _env: JNIEnv,
    _class: JClass,
) {
    println!("🔐 tfhe_init_keys called");
    let (client_key, server_key) = gen_keys();
    let cloud_key = server_key.clone(); // ✅ clone while you still have it

    let _ = CLIENT_KEY.set(client_key);
    let _ = SERVER_KEY.set(server_key);
    let _ = CLOUD_KEY.set(cloud_key); // also populate cloud key for export
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1encrypt_1byte(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) -> jlong {
    let key = CLIENT_KEY.get().expect("Client key not initialized");

    let len = env.get_array_length(&input).unwrap();
    let mut buffer = vec![0i8; len as usize];
    env.get_byte_array_region(&input, 0, &mut buffer).unwrap();

    let bit = buffer.first().copied().unwrap_or(0) != 0;
    println!("🔐 encrypt_byte: input = {:?}, bit = {}", buffer, bit);

    let ct = key.encrypt(bit);
    Box::into_raw(Box::new(ct)) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1decrypt_1bit(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let key = CLIENT_KEY.get().expect("Client key not initialized");
    assert!(ptr != 0, "Null pointer passed to decrypt");
    let ct = unsafe { &*(ptr as *mut Ciphertext) };
    if key.decrypt(ct) { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1and(
    _env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
) -> jlong {
    let key = SERVER_KEY.get().expect("Server key not initialized");
    assert!(a != 0 && b != 0, "Null pointer passed to and");
    let a = unsafe { &*(a as *mut Ciphertext) };
    let b = unsafe { &*(b as *mut Ciphertext) };
    let ct = key.and(a, b);
    Box::into_raw(Box::new(ct)) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1or(
    _env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
) -> jlong {
    let key = SERVER_KEY.get().expect("Server key not initialized");
    assert!(a != 0 && b != 0, "Null pointer passed to or");
    let a = unsafe { &*(a as *mut Ciphertext) };
    let b = unsafe { &*(b as *mut Ciphertext) };
    Box::into_raw(Box::new(key.or(a, b))) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1not(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jlong {
    let key = SERVER_KEY.get().expect("Server key not initialized");
    assert!(ptr != 0, "Null pointer passed to not");
    let ct = unsafe { &*(ptr as *mut Ciphertext) };
    Box::into_raw(Box::new(key.not(ct))) as jlong
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1xor(
    _env: JNIEnv,
    _class: JClass,
    a: jlong,
    b: jlong,
) -> jlong {
    let key = SERVER_KEY.get().expect("Server key not initialized");
    assert!(a != 0 && b != 0, "Null pointer passed to xor");
    let a = unsafe { &*(a as *mut Ciphertext) };
    let b = unsafe { &*(b as *mut Ciphertext) };
    let ct = key.xor(a, b);
    Box::into_raw(Box::new(ct)) as jlong
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
pub extern "C" fn Java_jniNative_TfheBridgeJNI_serialize_1ciphertext(
    env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jbyteArray {
    let ct = unsafe { &*(ptr as *const Ciphertext) };
    let bytes = bincode::serialize(ct).expect("Serialization failed");
    env.byte_array_from_slice(&bytes).expect("jbyteArray conversion failed").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1decrypt_1serialized(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray
) -> jboolean {
    let key = CLIENT_KEY.get().expect("Client key not initialized");
    let bytes = env.convert_byte_array(input).unwrap();
    let ct: Ciphertext = bincode::deserialize(&bytes).expect("Deserialization failed");
    key.decrypt(&ct) as u8
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_export_1client_1key(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    let key = CLIENT_KEY.get().expect("Client key not initialized");
    let bytes = bincode::serialize(key).expect("Serialization failed");
    env.byte_array_from_slice(&bytes).expect("Byte array conversion failed").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_import_1client_1key(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) {
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let key: ClientKey = bincode::deserialize(&bytes).expect("Failed to deserialize ClientKey");
    let _ = CLIENT_KEY.set(key);
}

#[no_mangle]
pub extern "system" fn Java_jniNative_TfheBridgeJNI_export_1cloud_1key(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    let key = CLOUD_KEY.get().expect("Cloud key not initialized");
    let serialized = bincode::serialize(key).expect("Serialization failed");
    env.byte_array_from_slice(&serialized).expect("Failed to convert cloud key to jbyteArray").as_raw()
}

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_import_1cloud_1key(
    env: JNIEnv,
    _class: JClass,
    input: JByteArray,
) {
    let bytes = env.convert_byte_array(input).expect("Invalid byte array");
    let key: ServerKey = bincode::deserialize(&bytes).expect("Failed to deserialize ServerKey");
    let _ = CLOUD_KEY.set(key.clone());
    let _ = SERVER_KEY.set(key); // Optional: set for logic reuse if needed
}

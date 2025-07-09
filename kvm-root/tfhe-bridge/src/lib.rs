use tfhe::boolean::prelude::*;
use std::sync::OnceLock;
use jni::JNIEnv;
use jni::objects::{JClass, JByteArray};
use jni::sys::{jbyteArray, jlong, jboolean};

// Global TFHE keys
static CLIENT_KEY: OnceLock<ClientKey> = OnceLock::new();
static SERVER_KEY: OnceLock<ServerKey> = OnceLock::new();

#[no_mangle]
pub extern "C" fn Java_jniNative_TfheBridgeJNI_tfhe_1init_1keys(
    _env: JNIEnv,
    _class: JClass,
) {
    println!("🔐 tfhe_init_keys called");
    let (client_key, server_key) = gen_keys();
    let _ = CLIENT_KEY.set(client_key);
    let _ = SERVER_KEY.set(server_key);
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
    println!("🔐 tfhe_and called with a={}, b={}", a, b);
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
pub extern "C" fn Java_jniNative_TfheBridgeJNI_echo_1ptr(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jlong {
    println!("🔁 echo_ptr received = {}", ptr);
    ptr
}

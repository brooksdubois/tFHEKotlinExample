use tfhe::boolean::prelude::*;
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jboolean, jlong, jbyte};

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

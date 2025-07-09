use tfhe::boolean::prelude::*;
use std::sync::OnceLock;

static CLIENT_KEY: OnceLock<ClientKey> = OnceLock::new();
static SERVER_KEY: OnceLock<ServerKey> = OnceLock::new();

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1init_1keys() {
    let (client_key, server_key) = gen_keys();
    let _ = CLIENT_KEY.set(client_key);
    let _ = SERVER_KEY.set(server_key);
}

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1encrypt_1bit(bit: bool) -> i64 {
    let key = CLIENT_KEY.get().expect("Key not initialized");
    let ct = key.encrypt(bit);
    Box::into_raw(Box::new(ct)) as i64
}

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1decrypt_1bit(ptr: i64) -> bool {
    let key = CLIENT_KEY.get().expect("Key not initialized");
    let ct = unsafe { &*(ptr as *mut Ciphertext) };
    key.decrypt(ct)
}

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1and(a: i64, b: i64) -> i64 {
    let key = SERVER_KEY.get().expect("Key not initialized");
    assert!(a != 0 && b != 0, "Null ciphertext pointer passed to tfhe_and");

    let a = unsafe { &*(a as *mut Ciphertext) };
    let b = unsafe { &*(b as *mut Ciphertext) };
    Box::into_raw(Box::new(key.and(a, b))) as i64
}

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1or(a: i64, b: i64) -> i64 {
    let key = SERVER_KEY.get().expect("Key not initialized");
    let a = unsafe { &*(a as *mut Ciphertext) };
    let b = unsafe { &*(b as *mut Ciphertext) };
    Box::into_raw(Box::new(key.or(a, b))) as i64
}

#[no_mangle]
pub extern "C" fn Java_kvm_native_TfheBridge_tfhe_1not(ptr: i64) -> i64 {
    let key = SERVER_KEY.get().expect("Key not initialized");
    let ct = unsafe { &*(ptr as *mut Ciphertext) };
    Box::into_raw(Box::new(key.not(ct))) as i64
}

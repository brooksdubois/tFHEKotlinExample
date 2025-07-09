package kvm.core

import kvm.encrpted.EncryptedBool
import kvm.model.SimpleRecord

class KVE {
    fun validateRecord(record: SimpleRecord): Boolean {
        val result: EncryptedBool = record.age.greaterThan(17)
        return result.decrypt()  // This would eventually stay encrypted
    }

    fun validateBatch(records: List<SimpleRecord>): Boolean {
        return records.all { validateRecord(it) }
    }
}

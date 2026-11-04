package com.heracles.support

import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.domain.StorageKey
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Files in memory, for tests that are not about S3 (S3FileStorageIT is). The "URL" carries the key and the validity. */
class InMemoryFileStorage : FileStorage {
    val files = ConcurrentHashMap<String, ByteArray>()
    val urlValidity = ConcurrentHashMap<String, Duration>()

    override fun put(key: StorageKey, content: ByteArray, contentType: String) { files[key.value] = content }
    override fun get(key: StorageKey): ByteArray? = files[key.value]
    override fun delete(key: StorageKey) { files.remove(key.value) }
    override fun downloadUrl(key: StorageKey, validFor: Duration): String =
        "memory://${key.value}?valid=${validFor.toSeconds()}".also { urlValidity[it] = validFor }
}

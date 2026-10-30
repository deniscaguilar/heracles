package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.FileStorage
import com.heracles.platform.domain.StorageKey
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

class S3FileStorage(private val s3: S3Client, private val presigner: S3Presigner, private val bucket: String) : FileStorage {
    override fun put(key: StorageKey, content: ByteArray, contentType: String) {
        s3.putObject({ it.bucket(bucket).key(key.value).contentType(contentType) }, RequestBody.fromBytes(content))
    }

    override fun get(key: StorageKey): ByteArray? =
        try {
            s3.getObjectAsBytes { it.bucket(bucket).key(key.value) }.asByteArray()
        } catch (e: NoSuchKeyException) {
            null
        }

    override fun delete(key: StorageKey) {
        s3.deleteObject { it.bucket(bucket).key(key.value) }
    }

    override fun downloadUrl(key: StorageKey, validFor: Duration): String =
        presigner.presignGetObject { it.signatureDuration(validFor).getObjectRequest { r -> r.bucket(bucket).key(key.value) } }.url().toString()
}

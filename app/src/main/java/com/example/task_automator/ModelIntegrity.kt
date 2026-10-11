package com.example.task_automator.helpers

import java.io.File
import java.security.MessageDigest

/** Verifies the entire bundle before it becomes visible to the inference engine. */
object ModelIntegrity {
    fun verify(
        file: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (Long) -> Unit = {}
    ) {
        require(file.isFile && file.length() == expectedSize) {
            "This file does not match the selected model's expected size. Import the original complete .litertlm bundle."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var checked = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                checked += count
                onProgress(checked)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        require(actualHash.equals(expectedSha256, ignoreCase = true)) {
            "Model verification failed. Import the original offline model file or download it again."
        }
    }
}

package com.example.task_automator

import com.example.task_automator.helpers.ModelIntegrity
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelIntegrityTest {
    @get:Rule val folder = TemporaryFolder()
    private val bundle = "test model content".toByteArray()
    private val digest = MessageDigest.getInstance("SHA-256").digest(bundle)
        .joinToString("") { "%02x".format(it) }

    private fun file(bytes: ByteArray): File = folder.newFile().apply { writeBytes(bytes) }

    @Test fun acceptsCompleteBundleAndReportsFullProgress() {
        var checked = 0L
        ModelIntegrity.verify(file(bundle), bundle.size.toLong(), digest) { checked = it }
        assertEquals(bundle.size.toLong(), checked)
    }

    @Test fun rejectsTruncatedDownload() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelIntegrity.verify(file(bundle.copyOf(bundle.size - 1)), bundle.size.toLong(), digest)
        }
    }

    @Test fun rejectsCorruptBundleEvenWhenSizeMatches() {
        val corrupt = bundle.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            ModelIntegrity.verify(file(corrupt), bundle.size.toLong(), digest)
        }
    }

    @Test fun cancellationDuringVerificationStopsImmediately() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ModelIntegrity.verify(file(bundle), bundle.size.toLong(), digest) {
                throw java.util.concurrent.CancellationException()
            }
        }
    }
}

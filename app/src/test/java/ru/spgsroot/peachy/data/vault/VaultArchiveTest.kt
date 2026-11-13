package ru.spgsroot.peachy.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.Random

class VaultArchiveTest {
    private val password = "correct horse".toCharArray()

    private fun sealed(block: (VaultArchiveWriter) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        VaultArchiveWriter(VaultCrypto.encrypt(out, password, VaultCrypto.MIN_ITERATIONS)).use(block)
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray) = VaultArchiveReader(VaultCrypto.decrypt(ByteArrayInputStream(bytes), password))

    @Test
    fun entriesComeBackInOrderWithTheirBytes() {
        val big = ByteArray(VaultCrypto.BLOCK_SIZE * 3 + 17).also(Random(7)::nextBytes)
        val vault = sealed {
            it.add("data.json", "{}".toByteArray())
            it.add("video/1.mp4", big.size.toLong(), ByteArrayInputStream(big))
            it.add("manga/5/1.jpg", byteArrayOf())
        }
        val reader = open(vault)
        val first = reader.next()!!
        assertEquals("data.json", first.name)
        assertEquals("{}", String(first.readBytes(100)))
        val second = reader.next()!!
        assertEquals("video/1.mp4", second.name)
        assertArrayEquals(big, second.stream.readBytes())
        val third = reader.next()!!
        assertEquals(0L, third.size)
        assertNull(reader.next())
    }

    @Test
    fun anUnreadEntryIsSkippedNotMisreadAsTheNext() {
        val vault = sealed {
            it.add("a", ByteArray(200_000) { 9 })
            it.add("b", byteArrayOf(1, 2, 3))
        }
        val reader = open(vault)
        reader.next()!!.stream.read(ByteArray(10))
        val b = reader.next()!!
        assertEquals("b", b.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), b.stream.readBytes())
    }

    @Test
    fun closingAnEntryStreamKeepsTheVaultReadable() {
        val vault = sealed {
            it.add("a", byteArrayOf(1))
            it.add("b", byteArrayOf(2))
        }
        val reader = open(vault)
        reader.next()!!.stream.use { it.readBytes() }
        assertEquals("b", reader.next()!!.name)
    }

    @Test
    fun aSourceShorterThanAnnouncedFailsTheExport() {
        val short: InputStream = ByteArrayInputStream(ByteArray(5))
        assertThrows(java.io.IOException::class.java) {
            sealed { it.add("a", 10, short) }
        }
    }

    @Test
    fun aVaultWithoutTheEndMarkerIsReportedAsCut() {
        val out = ByteArrayOutputStream()
        VaultCrypto.encrypt(out, password, VaultCrypto.MIN_ITERATIONS).use { sink ->
            DataOutputStream(sink).apply {
                writeByte(1)
                writeUTF("a")
                writeLong(1)
                writeByte(5)
            }
        }
        val reader = open(out.toByteArray())
        reader.next()!!.stream.readBytes()
        assertEquals(VaultError.CORRUPT, assertThrows(VaultException::class.java) { reader.next() }.error)
    }

    @Test
    fun anEntryThatEndsEarlyIsReportedAsCut() {
        val out = ByteArrayOutputStream()
        VaultCrypto.encrypt(out, password, VaultCrypto.MIN_ITERATIONS).use { sink ->
            DataOutputStream(sink).apply {
                writeByte(1)
                writeUTF("a")
                writeLong(10)
                write(ByteArray(3))
            }
        }
        val entry = open(out.toByteArray()).next()!!
        assertEquals(VaultError.CORRUPT, assertThrows(VaultException::class.java) { entry.stream.readBytes() }.error)
    }

    @Test
    fun hostileNamesAreRejectedOnRead() {
        for (name in listOf("../x", "a/../b", "/abs", "a//b", "a\\b", "", "./a", "a/.")) {
            val out = ByteArrayOutputStream()
            VaultCrypto.encrypt(out, password, VaultCrypto.MIN_ITERATIONS).use { sink ->
                DataOutputStream(sink).apply {
                    writeByte(1)
                    writeUTF(name)
                    writeLong(0)
                    writeByte(0)
                }
            }
            val error = assertThrows("name '$name'", VaultException::class.java) { open(out.toByteArray()).next() }
            assertEquals(VaultError.CORRUPT, error.error)
        }
    }

    @Test
    fun theWriterRefusesHostileNames() {
        assertThrows(IllegalArgumentException::class.java) { sealed { it.add("../escape", byteArrayOf()) } }
    }

    @Test
    fun safeNamesCoverTheNamesTheAppWrites() {
        assertTrue(VaultArchive.isSafeName("video/12.mp4"))
        assertTrue(VaultArchive.isSafeName("manga/604719/1t.webp"))
        assertFalse(VaultArchive.isSafeName("a\nb"))
    }
}

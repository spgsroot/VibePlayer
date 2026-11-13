package ru.spgsroot.peachy.data.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {
    private val password = "correct horse".toCharArray()
    private val iterations = VaultCrypto.MIN_ITERATIONS

    private fun seal(data: ByteArray, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCrypto.encrypt(out, pass, iterations).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(vault: ByteArray, pass: CharArray = password): ByteArray =
        VaultCrypto.decrypt(ByteArrayInputStream(vault), pass).use { it.readBytes() }

    private fun errorOf(vault: ByteArray, pass: CharArray = password): VaultError =
        assertThrows(VaultException::class.java) { open(vault, pass) }.error

    private fun payload(size: Int) = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }

    @Test
    fun payloadsAroundTheBlockBoundaryComeBackIdentical() {
        val block = VaultCrypto.BLOCK_SIZE
        for (size in listOf(0, 1, block - 1, block, block + 1, 3 * block, 3 * block + 17)) {
            val data = payload(size)
            assertArrayEquals("size $size", data, open(seal(data)))
        }
    }

    @Test
    fun writingByteByByteMatchesWritingInOneGo() {
        val data = payload(VaultCrypto.BLOCK_SIZE + 5)
        val out = ByteArrayOutputStream()
        VaultCrypto.encrypt(out, password, iterations).use { stream -> data.forEach { stream.write(it.toInt()) } }
        assertArrayEquals(data, open(out.toByteArray()))
    }

    @Test
    fun readingByteByByteWorks() {
        val data = payload(100)
        val stream = VaultCrypto.decrypt(ByteArrayInputStream(seal(data)), password)
        val read = ByteArrayOutputStream()
        while (true) {
            val b = stream.read()
            if (b < 0) break
            read.write(b)
        }
        assertArrayEquals(data, read.toByteArray())
    }

    @Test
    fun twoVaultsOfTheSameDataDiffer() {
        val data = payload(500)
        assertEquals(false, seal(data).contentEquals(seal(data)))
    }

    @Test
    fun wrongPasswordIsReportedAsSuch() {
        assertEquals(VaultError.WRONG_PASSWORD, errorOf(seal(payload(2000)), "wrong".toCharArray()))
    }

    @Test
    fun aFlippedBitInALaterBlockIsCorruption() {
        val vault = seal(payload(3 * VaultCrypto.BLOCK_SIZE))
        vault[vault.size - 100] = (vault[vault.size - 100].toInt() xor 1).toByte()
        assertEquals(VaultError.CORRUPT, errorOf(vault))
    }

    @Test
    fun aFlippedBitInTheHeaderFailsEvenWithTheRightPassword() {
        val vault = seal(payload(2000))
        // byte 9 is inside the salt: a different key, so the first block fails
        vault[9] = (vault[9].toInt() xor 1).toByte()
        assertEquals(VaultError.WRONG_PASSWORD, errorOf(vault))
    }

    @Test
    fun droppingTheLastBlockIsDetected() {
        val data = payload(2 * VaultCrypto.BLOCK_SIZE + 10)
        val vault = seal(data)
        // header 33 bytes, then two full blocks of 4 + BLOCK_SIZE + 16, then the final block
        val cut = 33 + 2 * (4 + VaultCrypto.BLOCK_SIZE + 16)
        assertEquals(VaultError.CORRUPT, errorOf(vault.copyOf(cut)))
    }

    @Test
    fun aVaultCutInTheMiddleOfABlockIsDetected() {
        val vault = seal(payload(5000))
        assertEquals(VaultError.CORRUPT, errorOf(vault.copyOf(vault.size - 3)))
    }

    @Test
    fun extraBytesAfterTheEndAreRejected() {
        assertEquals(VaultError.CORRUPT, errorOf(seal(payload(100)) + byteArrayOf(0)))
    }

    @Test
    fun blocksCannotBeSwapped() {
        val block = VaultCrypto.BLOCK_SIZE
        val vault = seal(payload(3 * block))
        val size = 4 + block + 16
        val first = vault.copyOfRange(33, 33 + size)
        val second = vault.copyOfRange(33 + size, 33 + 2 * size)
        val swapped = vault.copyOf()
        System.arraycopy(second, 0, swapped, 33, size)
        System.arraycopy(first, 0, swapped, 33 + size, size)
        assertEquals(VaultError.WRONG_PASSWORD, errorOf(swapped))
    }

    @Test
    fun otherFilesAreNotVaults() {
        assertEquals(VaultError.NOT_A_VAULT, errorOf(ByteArray(0)))
        assertEquals(VaultError.NOT_A_VAULT, errorOf("hello world, this is not a vault at all".toByteArray()))
    }

    @Test
    fun anUnknownVersionIsReported() {
        val vault = seal(payload(10))
        vault[4] = 2
        assertEquals(VaultError.UNSUPPORTED_VERSION, errorOf(vault))
    }

    @Test
    fun absurdKeyDerivationSettingsAreRefusedBeforeAnyWork() {
        val vault = seal(payload(10))
        ByteBuffer.wrap(vault).putInt(5, Int.MAX_VALUE)
        assertEquals(VaultError.CORRUPT, errorOf(vault))
        ByteBuffer.wrap(vault).putInt(5, 1)
        assertEquals(VaultError.CORRUPT, errorOf(vault))
    }
}

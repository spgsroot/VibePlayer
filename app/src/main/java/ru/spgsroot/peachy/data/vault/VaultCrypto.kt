package ru.spgsroot.peachy.data.vault

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a vault could not be read. [WRONG_PASSWORD] also covers a damaged first block: the two cannot be told apart. */
enum class VaultError { NOT_A_VAULT, UNSUPPORTED_VERSION, WRONG_PASSWORD, CORRUPT }

class VaultException(val error: VaultError, message: String) : IOException(message)

/**
 * Password-protected container. Layout:
 *
 *     "PCHV" | version(1) | PBKDF2 iterations(4) | salt(16) | nonce prefix(8) | block*
 *
 * The key is PBKDF2-HMAC-SHA256 of the password. The payload is cut into blocks of up to
 * [BLOCK_SIZE] bytes, each sealed with AES-256-GCM. A block is `length(4) | ciphertext`, where the
 * top bit of `length` marks the last block. The nonce is the prefix plus the block number, and the
 * additional data is the whole header plus the last-block flag, so a block cannot be moved, dropped
 * from the end, or carried over into another vault without the tag failing. Nothing is handed to
 * the reader before its block has been authenticated.
 */
object VaultCrypto {
    const val BLOCK_SIZE = 64 * 1024
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 5_000_000

    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 8
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + PREFIX_BYTES
    private const val LAST_FLAG = Int.MIN_VALUE
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'C'.code.toByte(), 'H'.code.toByte(), 'V'.code.toByte())
    private val random = SecureRandom()

    /** Writes the header to [out] and returns a stream that encrypts into it. Closing it finishes the vault. */
    fun encrypt(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations out of range" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION.toByte()).putInt(iterations).put(salt).put(prefix)
            .array()
        val key = deriveKey(password, salt, iterations)
        out.write(header)
        return EncryptingStream(out, key, header, prefix)
    }

    /** Reads and checks the header of [input] and returns a stream of the decrypted payload. */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER_BYTES)
        try {
            readFully(input, header)
        } catch (e: EOFException) {
            throw VaultException(VaultError.NOT_A_VAULT, "File is too short")
        }
        val buffer = ByteBuffer.wrap(header)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw VaultException(VaultError.NOT_A_VAULT, "Not a Peachy vault")
        val version = buffer.get().toInt()
        if (version != VERSION) throw VaultException(VaultError.UNSUPPORTED_VERSION, "Vault version $version")
        val iterations = buffer.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw VaultException(VaultError.CORRUPT, "Bad key derivation settings")
        }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val prefix = ByteArray(PREFIX_BYTES).also(buffer::get)
        return DecryptingStream(input, deriveKey(password, salt, iterations), header, prefix)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, counter: Long): ByteArray =
        ByteBuffer.allocate(PREFIX_BYTES + 4).put(prefix).putInt(counter.toInt()).array()

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, counter: Long, header: ByteArray, last: Boolean): Cipher {
        // A 32-bit block counter would repeat a nonce after 2^32 blocks (256 TiB).
        if (counter >= (1L shl 32)) throw IOException("Vault too large")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce(prefix, counter)))
            updateAAD(header)
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var read = 0
        while (read < target.size) {
            val n = input.read(target, read, target.size - read)
            if (n < 0) throw EOFException()
            read += n
        }
    }

    private class EncryptingStream(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray
    ) : OutputStream() {
        private val buffer = ByteArray(BLOCK_SIZE)
        private var filled = 0
        private var counter = 0L
        private var closed = false

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var offset = off
            var left = len
            while (left > 0) {
                // A full buffer is only sealed once more data follows, so the last block can be marked.
                if (filled == BLOCK_SIZE) seal(last = false)
                val n = minOf(left, BLOCK_SIZE - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n
                offset += n
                left -= n
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                seal(last = true)
                out.flush()
            } finally {
                out.close()
            }
        }

        private fun seal(last: Boolean) {
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter, header, last)
                .doFinal(buffer, 0, filled)
            out.write(ByteBuffer.allocate(4).putInt(if (last) sealed.size or LAST_FLAG else sealed.size).array())
            out.write(sealed)
            counter++
            filled = 0
        }
    }

    private class DecryptingStream(
        private val input: InputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val prefix: ByteArray
    ) : InputStream() {
        private var block = ByteArray(0)
        private var position = 0
        private var counter = 0L
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position == block.size) {
                if (finished) return -1
                nextBlock()
            }
            val n = minOf(len, block.size - position)
            System.arraycopy(block, position, b, off, n)
            position += n
            return n
        }

        override fun close() = input.close()

        private fun nextBlock() {
            val prefixBytes = ByteArray(4)
            val sealed: ByteArray
            val last: Boolean
            try {
                readFully(input, prefixBytes)
                val raw = ByteBuffer.wrap(prefixBytes).getInt()
                last = raw and LAST_FLAG != 0
                val length = raw and LAST_FLAG.inv()
                if (length < TAG_BYTES || length > BLOCK_SIZE + TAG_BYTES) {
                    throw VaultException(VaultError.CORRUPT, "Bad block size")
                }
                sealed = ByteArray(length)
                readFully(input, sealed)
            } catch (e: EOFException) {
                throw VaultException(VaultError.CORRUPT, "Vault is cut short")
            }
            block = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, counter, header, last).doFinal(sealed)
            } catch (e: AEADBadTagException) {
                throw if (counter == 0L) {
                    VaultException(VaultError.WRONG_PASSWORD, "Wrong password or damaged vault")
                } else {
                    VaultException(VaultError.CORRUPT, "Vault is damaged")
                }
            } catch (e: GeneralSecurityException) {
                throw VaultException(VaultError.CORRUPT, "Vault is damaged")
            }
            position = 0
            counter++
            if (last) {
                finished = true
                if (input.read() != -1) throw VaultException(VaultError.CORRUPT, "Data after the end of the vault")
            }
        }
    }
}

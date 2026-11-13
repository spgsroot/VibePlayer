package ru.spgsroot.peachy.data.vault

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A flat list of named blobs, written inside the encrypted vault stream:
 *
 *     (1 | name(UTF) | size(8) | bytes)* | 0
 *
 * Names are checked on read, so a damaged or hostile vault cannot point outside the folder an
 * entry is restored into.
 */
class VaultArchiveWriter(out: OutputStream) : Closeable {
    private val data = DataOutputStream(out)

    fun add(name: String, bytes: ByteArray) {
        header(name, bytes.size.toLong())
        data.write(bytes)
    }

    /** Copies exactly [size] bytes from [source]; a source that comes up short is an error. */
    fun add(name: String, size: Long, source: InputStream) {
        header(name, size)
        val buffer = ByteArray(COPY_BUFFER)
        var left = size
        while (left > 0) {
            val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IOException("$name is shorter than the $size bytes announced")
            data.write(buffer, 0, n)
            left -= n
        }
    }

    /** Writes the end marker and finishes the encrypted stream below. */
    override fun close() {
        data.writeByte(END)
        data.close()
    }

    private fun header(name: String, size: Long) {
        require(VaultArchive.isSafeName(name)) { "unsafe entry name" }
        require(size >= 0) { "negative size" }
        data.writeByte(ENTRY)
        data.writeUTF(name)
        data.writeLong(size)
    }
}

class VaultArchiveReader(input: InputStream) {
    private val data = DataInputStream(input)
    private var current: Entry? = null
    private var done = false

    /** The next entry, or null at the end of the vault. Whatever is left of the previous entry is skipped. */
    fun next(): Entry? {
        if (done) return null
        current?.drain()
        current = null
        val tag = try {
            data.readUnsignedByte()
        } catch (e: EOFException) {
            throw VaultException(VaultError.CORRUPT, "Vault is cut short")
        }
        when (tag) {
            END -> {
                done = true
                if (data.read() != -1) throw VaultException(VaultError.CORRUPT, "Data after the end of the vault")
                return null
            }
            ENTRY -> Unit
            else -> throw VaultException(VaultError.CORRUPT, "Bad entry marker")
        }
        val name = try {
            data.readUTF()
        } catch (e: EOFException) {
            throw VaultException(VaultError.CORRUPT, "Vault is cut short")
        }
        if (!VaultArchive.isSafeName(name)) throw VaultException(VaultError.CORRUPT, "Unsafe entry name")
        val size = try {
            data.readLong()
        } catch (e: EOFException) {
            throw VaultException(VaultError.CORRUPT, "Vault is cut short")
        }
        if (size < 0) throw VaultException(VaultError.CORRUPT, "Bad entry size")
        return Entry(name, size, data).also { current = it }
    }

    /** One entry. [stream] ends after [size] bytes; closing it leaves the vault open. */
    class Entry internal constructor(val name: String, val size: Long, private val source: InputStream) {
        private var left = size

        val stream: InputStream = object : InputStream() {
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (left == 0L) return -1
                val n = source.read(b, off, minOf(len.toLong(), left).toInt())
                if (n < 0) throw VaultException(VaultError.CORRUPT, "Vault is cut short")
                left -= n
                return n
            }

            override fun close() = Unit
        }

        /** The whole entry, for small ones such as the data document. */
        fun readBytes(limit: Int): ByteArray {
            if (size > limit) throw VaultException(VaultError.CORRUPT, "Entry $name is too large")
            val out = ByteArray(size.toInt())
            var read = 0
            while (read < out.size) {
                val n = stream.read(out, read, out.size - read)
                if (n < 0) throw VaultException(VaultError.CORRUPT, "Vault is cut short")
                read += n
            }
            return out
        }

        internal fun drain() {
            val sink = ByteArray(COPY_BUFFER)
            while (left > 0) {
                if (stream.read(sink, 0, sink.size) < 0) break
            }
        }
    }
}

object VaultArchive {
    private const val MAX_NAME = 512

    /** Relative, `/`-separated, no empty, `.` or `..` parts, no control characters or backslashes. */
    fun isSafeName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_NAME) return false
        if (name.any { it.isISOControl() || it == '\\' }) return false
        return name.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}

private const val ENTRY = 1
private const val END = 0
private const val COPY_BUFFER = 64 * 1024

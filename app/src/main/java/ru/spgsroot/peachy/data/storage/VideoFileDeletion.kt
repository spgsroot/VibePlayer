package ru.spgsroot.peachy.data.storage

import java.io.File

object VideoFileDeletion {
    fun resolve(videosDir: File, filePath: String): File? {
        if (filePath.isBlank()) return null
        val root = videosDir.canonicalFile
        val target = runCatching { File(filePath).canonicalFile }.getOrNull() ?: return null
        val rootPrefix = root.path + File.separator
        if (target != root && !target.path.startsWith(rootPrefix)) return null
        if (!target.isFile) return null
        return target
    }
}

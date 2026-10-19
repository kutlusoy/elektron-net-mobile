package org.elektronnet.mobile.console

import java.io.File
import java.nio.file.Files

enum class FileRefusal { Empty, Traversal, Outside, Symlink, Exists, Missing, BadName }

sealed interface Resolved {
    data class Ok(val file: File) : Resolved
    data class Refused(val reason: FileRefusal) : Resolved
}

class FileGuard(root: File) {
    private val root: File = root.canonicalFile

    fun resolve(input: String, subdir: String? = null, mustBeNew: Boolean = false, mustExist: Boolean = false): Resolved {
        val text = input.trim()
        if (text.isEmpty()) return Resolved.Refused(FileRefusal.Empty)
        if (text.length > MAX_NAME || text.any { it.code < 32 || it == '\\' }) return Resolved.Refused(FileRefusal.BadName)
        if (text.endsWith("/")) return Resolved.Refused(FileRefusal.BadName)
        if (text.split('/').any { it == ".." }) return Resolved.Refused(FileRefusal.Traversal)
        val base = if (subdir == null) root else File(root, subdir)
        val candidate = if (text.startsWith("/")) File(text) else File(base, text)
        val normalized = File(candidate.toURI().normalize().path)
        val rootPath = root.path + File.separator
        if (!normalized.path.startsWith(rootPath)) return Resolved.Refused(FileRefusal.Outside)
        if (normalized.name.isEmpty() || normalized.path.endsWith(File.separator)) return Resolved.Refused(FileRefusal.BadName)
        var walk: File? = normalized
        while (walk != null && walk.path != root.path) {
            if (walk.exists() && Files.isSymbolicLink(walk.toPath())) return Resolved.Refused(FileRefusal.Symlink)
            walk = walk.parentFile
        }
        val real = runCatching { normalized.canonicalFile }.getOrNull() ?: return Resolved.Refused(FileRefusal.BadName)
        if (normalized.exists() && !real.path.startsWith(rootPath)) return Resolved.Refused(FileRefusal.Outside)
        if (mustBeNew && normalized.exists()) return Resolved.Refused(FileRefusal.Exists)
        if (mustExist && !normalized.isFile) return Resolved.Refused(FileRefusal.Missing)
        normalized.parentFile?.mkdirs()
        return Resolved.Ok(normalized)
    }

    private companion object {
        const val MAX_NAME = 200
    }
}

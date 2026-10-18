package org.elektronnet.mobile.node

data class CoreManifest(val values: Map<String, String>) {
    val tag: String? get() = values["CORE_TAG"]?.takeIf { it.isNotEmpty() }
    val refName: String? get() = values["CORE_REF_NAME"]?.takeIf { it.isNotEmpty() }
    val commit: String? get() = values["CORE_SHA"]?.takeIf { it.isNotEmpty() }
    val libSha256: String? get() = values["LIB_SHA256"]?.takeIf { it.isNotEmpty() }

    fun label(): String? {
        val name = tag ?: refName ?: return null
        val short = commit?.take(SHORT)
        return if (short == null) name else "$name ($short)"
    }

    companion object {
        private const val SHORT = 8

        fun parse(text: String): CoreManifest = CoreManifest(
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
                .associate { it.substringBefore('=') to it.substringAfter('=') },
        )
    }
}

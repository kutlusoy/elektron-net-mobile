package org.elektronnet.mobile.geo

import java.net.InetAddress
import java.util.Locale

data class Country(val code: String, val name: String) {
    val flag: String
        get() = code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

class GeoIp(private val reader: MmdbReader?, private val language: String = Locale.getDefault().language) {
    private val cache = HashMap<String, Country?>()

    val available: Boolean get() = reader != null

    @Synchronized
    fun lookup(peerAddress: String): Country? {
        val host = numericHost(peerAddress) ?: return null
        return cache.getOrPut(host) { resolve(host) }
    }

    private fun resolve(host: String): Country? {
        val db = reader ?: return null
        return try {
            val address = InetAddress.getByName(host)
            if (!isPublic(address)) return null
            val country = db.lookup(address)?.get("country") as? Map<*, *> ?: return null
            val code = (country["iso_code"] as? String)?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: return null
            val names = country["names"] as? Map<*, *>
            val name = (names?.get(language) as? String)
                ?: (names?.get("en") as? String)
                ?: Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }
            Country(code, name)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val IPV6 = Regex("""[0-9A-Fa-f:.]+""")

        fun numericHost(address: String): String? {
            val raw = address.trim()
            if (raw.isEmpty()) return null
            if (raw.startsWith("[")) return raw.substringAfter('[').substringBefore(']').takeIf { it.contains(':') && IPV6.matches(it) }
            val colons = raw.count { it == ':' }
            return when {
                colons == 0 -> raw.takeIf { IPV4.matches(it) }
                colons == 1 -> raw.substringBefore(':').takeIf { IPV4.matches(it) }
                else -> raw.takeIf { IPV6.matches(it) }
            }
        }

        fun isPublic(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false
            val b = address.address
            if (b.size == 4) {
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                if (first == 100 && second in 64..127) return false
                if (first == 198 && second in 18..19) return false
                if (first == 192 && second == 0) return false
            } else if ((b[0].toInt() and 0xFE) == 0xFC) {
                return false
            }
            return true
        }
    }
}

package org.elektronnet.mobile.node

enum class TorRouting { Additional, AllTraffic }

enum class TorRole { Client, Relay, Exit }

enum class ExitPolicyKind { Reduced, Web }

data class TorSettings(
    val enabled: Boolean = false,
    val routing: TorRouting = TorRouting.Additional,
    val role: TorRole = TorRole.Client,
    val orPort: Int = DEFAULT_OR_PORT,
    val nickname: String = "",
    val contact: String = "",
    val bandwidthKbps: Int = DEFAULT_BANDWIDTH_KBPS,
    val monthlyLimitGb: Int = 0,
    val exitPolicy: ExitPolicyKind = ExitPolicyKind.Reduced,
) {
    fun normalized(): TorSettings = copy(
        orPort = orPort.coerceIn(MIN_PORT, MAX_PORT),
        nickname = nickname.filter { it.isLetterOrDigit() && it.code < 128 }.take(MAX_NICKNAME),
        contact = contact.filter { it != '\n' && it != '\r' && !it.isISOControl() }.take(MAX_CONTACT).trim(),
        bandwidthKbps = bandwidthKbps.coerceIn(MIN_BANDWIDTH_KBPS, MAX_BANDWIDTH_KBPS),
        monthlyLimitGb = monthlyLimitGb.coerceIn(0, MAX_MONTHLY_GB),
    )

    companion object {
        const val DEFAULT_OR_PORT = 9001
        const val DEFAULT_BANDWIDTH_KBPS = 256
        const val MIN_BANDWIDTH_KBPS = 75
        const val MAX_BANDWIDTH_KBPS = 8192
        const val MAX_MONTHLY_GB = 2048
        const val MAX_NICKNAME = 19
        const val MAX_CONTACT = 100
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
    }
}

data class I2pSettings(
    val enabled: Boolean = false,
    val acceptIncoming: Boolean = true,
    val transit: Boolean = false,
    val bandwidthKbps: Int = DEFAULT_BANDWIDTH_KBPS,
    val sharePercent: Int = DEFAULT_SHARE_PERCENT,
) {
    fun normalized(): I2pSettings = copy(
        bandwidthKbps = bandwidthKbps.coerceIn(MIN_BANDWIDTH_KBPS, MAX_BANDWIDTH_KBPS),
        sharePercent = sharePercent.coerceIn(0, 100),
    )

    companion object {
        const val DEFAULT_BANDWIDTH_KBPS = 256
        const val MIN_BANDWIDTH_KBPS = 32
        const val MAX_BANDWIDTH_KBPS = 8192
        const val DEFAULT_SHARE_PERCENT = 50
    }
}

data class NetworkSettings(
    val tor: TorSettings = TorSettings(),
    val i2p: I2pSettings = I2pSettings(),
) {
    fun normalized(): NetworkSettings = NetworkSettings(tor.normalized(), i2p.normalized())
}

data class NetworkPorts(
    val torSocks: Int = 9050,
    val torControl: Int = 9051,
    val i2pSam: Int = 7656,
) {
    companion object {
        val Testnet = NetworkPorts(torSocks = 19050, torControl = 19051, i2pSam = 17656)
    }
}

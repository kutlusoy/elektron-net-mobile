package org.elektronnet.mobile.node

enum class RelayReach { Unknown, Checking, Reachable, Unreachable }

data class TorLogStatus(val bootstrap: Int?, val reach: RelayReach) {
    companion object {
        private val bootstrapLine = Regex("""Bootstrapped (\d{1,3})%""")

        fun parse(lines: List<String>): TorLogStatus {
            var bootstrap: Int? = null
            var reach = RelayReach.Unknown
            for (line in lines) {
                bootstrapLine.find(line)?.let { bootstrap = it.groupValues[1].toInt().coerceIn(0, 100) }
                when {
                    line.contains("is reachable from the outside") -> reach = RelayReach.Reachable
                    line.contains("has not managed to confirm") || line.contains("is not reachable from the outside") -> reach = RelayReach.Unreachable
                    line.contains("Now checking whether") && line.contains("ORPort") -> if (reach != RelayReach.Reachable) reach = RelayReach.Checking
                }
            }
            return TorLogStatus(bootstrap, reach)
        }
    }
}

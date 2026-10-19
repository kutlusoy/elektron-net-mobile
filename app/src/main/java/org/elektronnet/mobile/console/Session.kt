package org.elektronnet.mobile.console

class ConsoleSession(private val clock: () -> Long = System::currentTimeMillis, private val timeoutMs: Long = TIMEOUT_MS) {
    var unlocked: Boolean = false
        private set
    var dangerEnabled: Boolean = false
        private set
    private var lastActivity = 0L

    val active: Boolean get() = unlocked && !expired()

    fun unlock() {
        unlocked = true
        dangerEnabled = false
        lastActivity = clock()
    }

    fun touch() {
        if (expired()) end() else lastActivity = clock()
    }

    fun expired(): Boolean = unlocked && clock() - lastActivity >= timeoutMs

    fun enableDanger() {
        if (active) dangerEnabled = true
    }

    fun disableDanger() {
        dangerEnabled = false
    }

    fun end() {
        unlocked = false
        dangerEnabled = false
    }

    companion object {
        const val TIMEOUT_MS = 30 * 60 * 1000L
        const val DANGER_TOKEN = "I-KNOW-THE-RISK"
    }
}

sealed interface Decision {
    data object Run : Decision
    data object NeedsConfirm : Decision
    data object NeedsDangerConfirm : Decision
    data object DangerSwitchOff : Decision
    data object SessionExpired : Decision
    data object Blocked : Decision
}

object Gate {
    fun decide(tier: Tier, session: ConsoleSession): Decision = when {
        !session.active -> Decision.SessionExpired
        tier == Tier.Blocked -> Decision.Blocked
        tier == Tier.Open -> Decision.Run
        tier == Tier.Confirm -> Decision.NeedsConfirm
        !session.dangerEnabled -> Decision.DangerSwitchOff
        else -> Decision.NeedsDangerConfirm
    }
}

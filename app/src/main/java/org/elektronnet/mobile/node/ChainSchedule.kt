package org.elektronnet.mobile.node

data class ChainRules(
    val halvingInterval: Long = 2_102_400L,
    val retargetInterval: Long = 2016L,
    val targetSpacingSeconds: Long = 60L,
    val initialRewardLep: Long = 500_000_000L,
)

data class ChainSchedule(
    val height: Long,
    val nextHalvingHeight: Long,
    val blocksToHalving: Long,
    val halvingAtMs: Long,
    val rewardNowLep: Long,
    val rewardNextLep: Long,
    val epochBlocksMined: Long,
    val epochLength: Long,
    val epochElapsedSeconds: Long?,
    val avgBlockSeconds: Double?,
    val retargetBlocksLeft: Long,
    val retargetAtMs: Long,
    val difficultyFactor: Double?,
    val projectedDifficulty: Double?,
) {
    companion object {
        private const val MAX_FACTOR = 4.0

        fun compute(height: Long, tipTimeSeconds: Long, epochStartTimeSeconds: Long?, difficulty: Double?, rules: ChainRules = ChainRules()): ChainSchedule {
            val halvings = height / rules.halvingInterval
            val nextHalving = (halvings + 1) * rules.halvingInterval
            val toHalving = nextHalving - height
            val tipMs = tipTimeSeconds * 1000
            val spacingMs = rules.targetSpacingSeconds * 1000
            val mined = height % rules.retargetInterval
            val elapsed = epochStartTimeSeconds?.let { tipTimeSeconds - it }?.takeIf { it >= 0 && mined > 0 }
            val avg = elapsed?.let { it.toDouble() / mined }
            val factor = avg?.let {
                val actual = it * (rules.retargetInterval - 1)
                (rules.retargetInterval * rules.targetSpacingSeconds / actual).coerceIn(1.0 / MAX_FACTOR, MAX_FACTOR)
            }
            val left = rules.retargetInterval - mined
            return ChainSchedule(
                height = height,
                nextHalvingHeight = nextHalving,
                blocksToHalving = toHalving,
                halvingAtMs = tipMs + toHalving * spacingMs,
                rewardNowLep = reward(halvings, rules),
                rewardNextLep = reward(halvings + 1, rules),
                epochBlocksMined = mined,
                epochLength = rules.retargetInterval,
                epochElapsedSeconds = elapsed,
                avgBlockSeconds = avg,
                retargetBlocksLeft = left,
                retargetAtMs = tipMs + left * spacingMs,
                difficultyFactor = factor,
                projectedDifficulty = if (difficulty != null && factor != null) difficulty * factor else null,
            )
        }

        private fun reward(halvings: Long, rules: ChainRules): Long = if (halvings >= 64) 0L else rules.initialRewardLep shr halvings.toInt()
    }
}

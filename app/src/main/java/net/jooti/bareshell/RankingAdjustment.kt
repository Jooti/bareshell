package net.jooti.bareshell

internal data class RankingAdjustment(val points: Int = 0, val decayDay: Long = 0) {
    fun decay(today: Long): RankingAdjustment {
        val days = (today - decayDay).coerceAtLeast(0)
        val remaining = if (points >= 0) {
            (points.toLong() - days).coerceAtLeast(0)
        } else {
            (points.toLong() + days).coerceAtMost(0)
        }
        return RankingAdjustment(remaining.toInt(), maxOf(today, decayDay))
    }

    fun adjust(delta: Int, today: Long): RankingAdjustment {
        require(delta == 10 || delta == -10)
        val current = decay(today)
        return current.copy(points = boundedPoints(current.points.toLong() + delta))
    }

    fun merge(other: RankingAdjustment): RankingAdjustment = RankingAdjustment(
        boundedPoints(points.toLong() + other.points), maxOf(decayDay, other.decayDay),
    )
}

private fun boundedPoints(points: Long): Int =
    points.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

internal fun addUsage(total: Long, value: Long): Long {
    val positive = value.coerceAtLeast(0)
    return if (total > Long.MAX_VALUE - positive) Long.MAX_VALUE else total + positive
}

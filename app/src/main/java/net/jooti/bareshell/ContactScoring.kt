package net.jooti.bareshell

internal data class ContactScore(
    val points: Int, val decayDay: Long, val lastInteraction: Long,
    val adjustment: RankingAdjustment = RankingAdjustment(),
) {
    init { require(points >= 0) }

    fun decay(today: Long): ContactScore = copy(
        points = (points.toLong() - (today - decayDay).coerceAtLeast(0)).coerceAtLeast(0).toInt(),
        decayDay = maxOf(today, decayDay),
        adjustment = if (adjustment.points == 0) adjustment else adjustment.decay(today),
    )

    fun interact(today: Long, now: Long): ContactScore {
        val current = decay(today)
        return current.copy(
            points = if (current.points == Int.MAX_VALUE) Int.MAX_VALUE else current.points + 1,
            lastInteraction = maxOf(now, lastInteraction),
        )
    }

    fun merge(other: ContactScore): ContactScore = ContactScore(
        (points.toLong() + other.points).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        maxOf(decayDay, other.decayDay), maxOf(lastInteraction, other.lastInteraction),
        adjustment.merge(other.adjustment),
    )
}

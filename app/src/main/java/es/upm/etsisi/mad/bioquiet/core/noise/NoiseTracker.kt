package es.upm.etsisi.mad.bioquiet.core.noise

import es.upm.etsisi.mad.bioquiet.model.NoiseThresholds

enum class NoiseLevel { SAFE, CAUTION, WARNING }

class NoiseTracker(
    private val windowSize: Int = 5,
    private val warningThreshold: Int = 3
) {
    private val history = ArrayDeque<Boolean>(windowSize)

    fun record(db: Double, thresholds: NoiseThresholds): NoiseLevel {
        val exceeded = db >= thresholds.dbWarning
        if (history.size >= windowSize) history.removeFirst()
        history.addLast(exceeded)

        return when {
            db >= thresholds.dbWarning -> NoiseLevel.WARNING
            db >= thresholds.dbSafe -> NoiseLevel.CAUTION
            else -> NoiseLevel.SAFE
        }
    }

    val isSustainedWarning: Boolean
        get() = history.count { it } >= warningThreshold

    fun reset() {
        history.clear()
    }
}

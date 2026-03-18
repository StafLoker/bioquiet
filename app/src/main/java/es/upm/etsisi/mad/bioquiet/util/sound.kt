package es.upm.etsisi.mad.bioquiet.util

import kotlin.math.log10

fun amplitude2db(amplitude: Double) : Double = if (amplitude > 0) 20 * log10(amplitude) else 0.0

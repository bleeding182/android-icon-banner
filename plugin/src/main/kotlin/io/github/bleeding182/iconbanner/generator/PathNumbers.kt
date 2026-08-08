package io.github.bleeding182.iconbanner.generator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The single place a number becomes text in `pathData`.
 *
 * Locale is the trap: `"%.2f".format(23.1)` is `23,10` on a comma-decimal JVM, which turns one
 * coordinate into two. [java.math.BigDecimal.toPlainString] has no locale to get wrong.
 *
 * Rounds to [SCALE] decimals and strips trailing zeros, keeping golden diffs small.
 */
internal object PathNumbers {

    /** Decimal places kept in `pathData`. Two is well below a pixel at any launcher icon size. */
    const val SCALE: Int = 2

    fun format(value: Double): String {
        require(value.isFinite()) { "Cannot write a non-finite number into pathData: $value" }
        // valueOf goes through Double.toString, which is locale-independent.
        val rounded = BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP)
        // BigDecimal has no negative zero, so -0.001 rounds to "0" rather than "-0".
        val stripped = if (rounded.signum() == 0) BigDecimal.ZERO else rounded.stripTrailingZeros()
        return stripped.toPlainString()
    }
}

/** Shorthand for [PathNumbers.format], used pervasively while building path data. */
internal fun Double.toPathNumber(): String = PathNumbers.format(this)

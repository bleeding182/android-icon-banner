package com.github.bleeding182.iconbanner.generator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The single place any number becomes text in generated `pathData`.
 *
 * Locale is the trap this exists to close. On a JVM whose default locale uses a comma decimal
 * separator, `"%.2f".format(23.1)` yields `23,10`, which turns every coordinate in a generated
 * VectorDrawable into two coordinates and silently corrupts the icon. [BigDecimal.toPlainString] is
 * locale-independent by construction, so there is no locale to get wrong here — no formatter, no
 * `Locale.ROOT` argument that a later edit could drop.
 *
 * Rounds to [SCALE] decimals and strips trailing zeros, so `23.10` prints as `23.1` and `23.00` as
 * `23`. That keeps golden files readable and diffs small.
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

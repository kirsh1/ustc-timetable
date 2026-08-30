package com.ustc.timetable.timetable.ui

import java.security.MessageDigest
import androidx.compose.ui.graphics.Color

/**
 * 同一学期同一课程稳定同色（SPEC §4.7）：paletteIndex = MD5(colorKey)[0] % 12，
 * unsigned byte（[0] & 0xFF）后对 12 取模。
 */
object CoursePalette {
    const val NON_CURRENT_WEEK_ALPHA: Float = 0.35f

    private val palette: List<Pair<Color, Color>> = listOf(
        Color(0xFFE8DEF8) to Color(0xFF21005D),
        Color(0xFFD0BCFF) to Color(0xFF381E72),
        Color(0xFFCCC2DC) to Color(0xFF332D41),
        Color(0xFFEADDFF) to Color(0xFF4F378B),
        Color(0xFFECDDFE) to Color(0xFF432C81),
        Color(0xFFF2B8B5) to Color(0xFF601410),
        Color(0xFFFFDCC7) to Color(0xFF5E2C00),
        Color(0xFFFFECB4) to Color(0xFF451B00),
        Color(0xFFFFDBCB) to Color(0xFF5E2C00),
        Color(0xFFFFD8E4) to Color(0xFF633B48),
        Color(0xFFFFD9E3) to Color(0xFF633B48),
        Color(0xFFFFEFB9) to Color(0xFF524100),
    )

    fun colorIndexFor(colorKey: String): Int {
        val first = MessageDigest.getInstance("MD5")
            .digest(colorKey.toByteArray(Charsets.UTF_8))[0]
            .toInt() and 0xFF
        return first % 12
    }

    fun alphaFor(isCurrentWeek: Boolean, showNonCurrentWeek: Boolean): Float =
        if (!isCurrentWeek && showNonCurrentWeek) NON_CURRENT_WEEK_ALPHA else 1f

    fun containerColor(index: Int): Color = palette[index.coerceIn(0, palette.size - 1)].first
    fun onContainerColor(index: Int): Color = palette[index.coerceIn(0, palette.size - 1)].second
}

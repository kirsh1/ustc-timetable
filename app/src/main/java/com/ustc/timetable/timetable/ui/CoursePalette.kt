package com.ustc.timetable.timetable.ui

import androidx.compose.ui.graphics.Color
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 稳定课程配色（SPEC §4.7）：paletteIndex = MD5(colorKey)[0]（unsigned byte）% 12。
 * 同一学期同一课程（stable colorKey）跨启动同色；非当前周淡化策略为纯函数。
 */
object CoursePalette {

    const val NON_CURRENT_WEEK_ALPHA: Float = 0.35f
    const val PALETTE_SIZE: Int = 12

    private val pairs: List<Pair<Color, Color>> = listOf(
        Color(0xFFB9DCFF) to Color(0xFF102A43),
        Color(0xFFE0C7F2) to Color(0xFF32164A),
        Color(0xFFBFE3C4) to Color(0xFF17351C),
        Color(0xFFFFD1AD) to Color(0xFF4A2200),
        Color(0xFFAFE2E7) to Color(0xFF07383C),
        Color(0xFFF2C3D2) to Color(0xFF4A1427),
        Color(0xFFF0DA91) to Color(0xFF3D3000),
        Color(0xFFC9D3D8) to Color(0xFF1E2B31),
        Color(0xFFC8CEEE) to Color(0xFF202B5C),
        Color(0xFFD8C9C2) to Color(0xFF39251C),
        Color(0xFFB7DDD6) to Color(0xFF12362F),
        Color(0xFFF1C5B9) to Color(0xFF4A1E14),
    )

    init {
        require(pairs.size == PALETTE_SIZE)
    }

    /** MD5 首字节按 unsigned（& 0xFF）对 12 取模。 */
    fun colorIndexFor(colorKey: String): Int {
        val first = MessageDigest.getInstance("MD5")
            .digest(colorKey.toByteArray(Charsets.UTF_8))[0]
            .toInt() and 0xFF
        return first % PALETTE_SIZE
    }

    /** 当前周恒 1f；非当前周仅在开关开启时淡化，开关关闭时不渲染（由调用方跳过）。 */
    fun alphaFor(isCurrentWeek: Boolean, showNonCurrentWeek: Boolean): Float =
        if (!isCurrentWeek && showNonCurrentWeek) NON_CURRENT_WEEK_ALPHA else 1f

    fun containerColor(index: Int): Color = pairs[index].first

    fun onContainerColor(index: Int): Color = pairs[index].second

    fun contrastRatio(index: Int): Double {
        val a = relativeLuminance(containerColor(index))
        val b = relativeLuminance(onContainerColor(index))
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}

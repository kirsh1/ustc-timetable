package com.ustc.timetable.timetable.ui

import androidx.compose.ui.graphics.Color
import java.security.MessageDigest

/**
 * 稳定课程配色（SPEC §4.7）：paletteIndex = MD5(colorKey)[0]（unsigned byte）% 12。
 * 同一学期同一课程（stable colorKey）跨启动同色；非当前周淡化策略为纯函数。
 */
object CoursePalette {

    const val NON_CURRENT_WEEK_ALPHA: Float = 0.35f
    const val PALETTE_SIZE: Int = 12

    private val pairs: List<Pair<Color, Color>> = listOf(
        Color(0xFFE1F5FE) to Color(0xFF0D47A1),
        Color(0xFFF3E5F5) to Color(0xFF4A148C),
        Color(0xFFE8F5E9) to Color(0xFF1B5E20),
        Color(0xFFFFF3E0) to Color(0xFFE65100),
        Color(0xFFE0F7FA) to Color(0xFF006064),
        Color(0xFFFCE4EC) to Color(0xFF880E4F),
        Color(0xFFFFFDE7) to Color(0xFFF57F17),
        Color(0xFFECEFF1) to Color(0xFF263238),
        Color(0xFFE8EAF6) to Color(0xFF283593),
        Color(0xFFEFEBE9) to Color(0xFF3E2723),
        Color(0xFFE0F2F1) to Color(0xFF004D40),
        Color(0xFFFBE9E7) to Color(0xFFBF360C),
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
}

package com.ustc.timetable.timetable.domain

/**
 * 周集合位掩码值类型（SPEC §3.1）。第 w 周（1-based）对应 bit (w-1)，容量 63 周。
 * 规范文本形式见 [format]/[parse]；"单周/双周" 语义由 [oddWithin]/[evenWithin] 表达。
 */
@JvmInline
value class WeekPattern(val mask: Long) {

    operator fun contains(week: Int): Boolean =
        week in 1..63 && (mask shr (week - 1) and 1L) == 1L

    fun union(other: WeekPattern): WeekPattern = WeekPattern(mask or other.mask)

    /** 规范形式：逗号分隔的升序单值与闭区间，如 "2-6,8,10-12"。 */
    fun format(): String {
        if (mask == 0L) return ""
        val parts = mutableListOf<String>()
        var w = 1
        while (w <= 63) {
            if (contains(w)) {
                var end = w
                while (end + 1 <= 63 && contains(end + 1)) end++
                parts += if (end == w) "$w" else "$w-$end"
                w = end + 1
            } else {
                w++
            }
        }
        return parts.joinToString(",")
    }

    companion object {
        val EMPTY = WeekPattern(0L)

        fun of(vararg weeks: Int): WeekPattern {
            require(weeks.isNotEmpty()) { "empty week set" }
            var m = 0L
            for (w in weeks) {
                require(w in 1..63) { "week out of range: $w" }
                m = m or (1L shl (w - 1))
            }
            return WeekPattern(m)
        }

        fun range(start: Int, endInclusive: Int): WeekPattern {
            require(start in 1..63 && endInclusive in start..63) { "bad range $start-$endInclusive" }
            var m = 0L
            for (w in start..endInclusive) m = m or (1L shl (w - 1))
            return WeekPattern(m)
        }

        fun oddWithin(start: Int, endInclusive: Int): WeekPattern = parityWithin(start, endInclusive, odd = true)

        fun evenWithin(start: Int, endInclusive: Int): WeekPattern = parityWithin(start, endInclusive, odd = false)

        private fun parityWithin(start: Int, endInclusive: Int, odd: Boolean): WeekPattern {
            require(start in 1..63 && endInclusive in start..63) { "bad range $start-$endInclusive" }
            var m = 0L
            for (w in start..endInclusive) {
                if ((w % 2 == 1) == odd) m = m or (1L shl (w - 1))
            }
            require(m != 0L) { "parity selection produced empty set: $start-$endInclusive" }
            return WeekPattern(m)
        }

        private val GRAMMAR = Regex("""\d+(-\d+)?(,\d+(-\d+)?)*""")

        /**
         * 解析数字语法："2-6,8,10-12"。仅规范化明确允许的装饰（，/、→逗号，–/—/至/到→连字符，
         * 删除 第/周/whitespace），随后整个字符串必须匹配 grammar；出现任何其他字符（如 "2foo" 的
         * 'f'）直接抛 IllegalArgumentException，绝不静默过滤，防止 "2a3" 之类被误容错为 "23"。
         */
        fun parse(raw: String): WeekPattern {
            val cleaned = buildString {
                for (ch in raw) {
                    when (ch) {
                        '，', '、' -> append(',')
                        '–', '—', '至', '到' -> append('-')
                        '第', '周', ' ', '\t', '\r', '\n' -> { /* 允许的装饰：丢弃 */ }
                        else -> {
                            if (!(ch.isDigit() || ch == ',' || ch == '-')) {
                                throw IllegalArgumentException("unexpected character '$ch' in week pattern: $raw")
                            }
                            append(ch)
                        }
                    }
                }
            }
            require(GRAMMAR.matches(cleaned)) { "not a week pattern: $raw" }
            var m = 0L
            for (token in cleaned.split(',')) {
                val seg = token.split('-')
                val a = seg[0].toInt()
                val b = if (seg.size == 2) seg[1].toInt() else a
                require(b >= a) { "inverted range: $token" }
                require(a in 1..63 && b in 1..63) { "week out of range in: $token" }
                for (w in a..b) m = m or (1L shl (w - 1))
            }
            require(m != 0L) { "empty week pattern: $raw" }
            return WeekPattern(m)
        }
    }
}

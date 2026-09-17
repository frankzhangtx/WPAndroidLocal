package org.wordpress.android.workers.weeklyroundup

import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object WeeklyRoundupUtils {
    private const val STANDARD_DATE_PATTERN = "uuuu-MM-dd"
    private const val WEEK_PERIOD_DATE_PATTERN = "uuuu'W'MM'W'dd"

    private val standardFormatter = DateTimeFormatter
        .ofPattern(STANDARD_DATE_PATTERN, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    private val weekPeriodFormatter = DateTimeFormatter
        .ofPattern(WEEK_PERIOD_DATE_PATTERN, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun parseStandardDate(date: String): LocalDate? = safelyParseDate(date, standardFormatter)

    fun parseWeekPeriodDate(date: String): LocalDate? = safelyParseDate(date, weekPeriodFormatter)

    private fun safelyParseDate(
        date: String,
        formatter: DateTimeFormatter
    ) = runCatching { LocalDate.parse(date, formatter) }
        .onFailure { AppLog.e(T.NOTIFS, "Weekly Roundup – Couldn't parse date: $date", it) }
        .getOrNull()
}

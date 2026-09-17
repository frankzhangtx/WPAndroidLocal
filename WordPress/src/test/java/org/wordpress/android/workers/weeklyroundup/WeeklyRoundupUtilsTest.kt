package org.wordpress.android.workers.weeklyroundup

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.time.LocalDate

@RunWith(MockitoJUnitRunner::class)
class WeeklyRoundupUtilsTest {
    @Test
    fun `parsing standard date returns correct value`() {
        val date = WeeklyRoundupUtils.parseStandardDate("2021-08-10")
        assertThat(date).isNotNull
        assertThat(date).isEqualTo(LocalDate.of(2021, 8, 10))
    }

    @Test
    fun `parsing week period date returns correct value`() {
        val date = WeeklyRoundupUtils.parseWeekPeriodDate("2021W08W10")
        assertThat(date).isNotNull
        assertThat(date).isEqualTo(LocalDate.of(2021, 8, 10))
    }

    @Test
    fun `parsing invalid date returns null`() {
        val date = WeeklyRoundupUtils.parseWeekPeriodDate("invalid")
        assertThat(date).isNull()
    }

    @Test
    fun `parsing standard date with non-existent day returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-02-30")).isNull()
    }

    @Test
    fun `parsing week period date with non-existent day returns null`() {
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W02W30")).isNull()
    }

    @Test
    fun `parsing non-existent day in 30 day month returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-04-31")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-06-31")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W04W31")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W06W31")).isNull()
    }

    @Test
    fun `parsing non-leap year february 29 returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2023-02-29")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2023W02W29")).isNull()
    }

    @Test
    fun `parsing leap day returns correct value`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2024W02W29")).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test
    fun `parsing out of range month or day returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-13-01")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-00-10")).isNull()
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-08-00")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W13W01")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W00W10")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("2021W08W00")).isNull()
    }

    @Test
    fun `parsing year boundary dates returns correct value`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-01-01")).isEqualTo(LocalDate.of(2021, 1, 1))
        assertThat(WeeklyRoundupUtils.parseStandardDate("2021-12-31")).isEqualTo(LocalDate.of(2021, 12, 31))
    }

    @Test
    fun `parsing empty date returns null`() {
        assertThat(WeeklyRoundupUtils.parseStandardDate("")).isNull()
        assertThat(WeeklyRoundupUtils.parseWeekPeriodDate("")).isNull()
    }
}

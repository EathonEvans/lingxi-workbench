package com.lingxi.workbench.application.runtime.service.trigger;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerScheduleCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldParseSixFieldCron() {
        assertTrue(TriggerScheduleCalculator.parseCron("0 0 9 * * ?") instanceof org.springframework.scheduling.support.CronExpression);
        assertThrows(IllegalArgumentException.class, () -> TriggerScheduleCalculator.parseCron("0 0 9 * *"));
    }

    @Test
    void shouldComputeNextFireInTriggerTimezone() {
        // 2026-10-03 08:00 +08 之后最近一次 9 点触发是当天
        ZonedDateTime after = ZonedDateTime.of(2026, 10, 3, 8, 0, 0, 0, ZONE);
        ZonedDateTime next = TriggerScheduleCalculator.nextFire("0 0 9 * * ?", "Asia/Shanghai", after);

        assertEquals(2026, next.getYear());
        assertEquals(10, next.getMonthValue());
        assertEquals(3, next.getDayOfMonth());
        assertEquals(9, next.getHour());
        assertEquals(ZONE, next.getZone());
    }

    @Test
    void shouldNotShiftWhenOutsideQuietHours() {
        ZonedDateTime fireTime = ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZONE);

        assertEquals(fireTime, TriggerScheduleCalculator.shiftIfQuietHours(fireTime, "22:00-08:00"));
        assertEquals(fireTime, TriggerScheduleCalculator.shiftIfQuietHours(fireTime, null));
        assertEquals(fireTime, TriggerScheduleCalculator.shiftIfQuietHours(fireTime, " "));
    }

    @Test
    void shouldShiftToEndWhenInsideSameDayQuietWindow() {
        ZonedDateTime fireTime = ZonedDateTime.of(2026, 10, 3, 22, 30, 0, 0, ZONE);

        ZonedDateTime shifted = TriggerScheduleCalculator.shiftIfQuietHours(fireTime, "22:00-23:00");

        assertEquals(LocalDateTime.of(2026, 10, 3, 23, 0), shifted.toLocalDateTime());
    }

    @Test
    void shouldShiftToMorningEndForCrossMidnightWindow() {
        ZonedDateTime eveningFire = ZonedDateTime.of(2026, 10, 3, 23, 30, 0, 0, ZONE);
        ZonedDateTime dawnFire = ZonedDateTime.of(2026, 10, 3, 3, 0, 0, 0, ZONE);

        assertEquals(LocalDateTime.of(2026, 10, 4, 8, 0),
                TriggerScheduleCalculator.shiftIfQuietHours(eveningFire, "22:00-08:00").toLocalDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 0),
                TriggerScheduleCalculator.shiftIfQuietHours(dawnFire, "22:00-08:00").toLocalDateTime());
    }

    @Test
    void shouldRejectMalformedQuietHours() {
        ZonedDateTime fireTime = ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZONE);

        assertThrows(IllegalArgumentException.class,
                () -> TriggerScheduleCalculator.shiftIfQuietHours(fireTime, "9点-10点"));
    }

    @Test
    void shouldRenderNowVariableInGoalTemplate() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 3, 9, 0, 0, 0, ZONE);

        String rendered = TriggerScheduleCalculator.renderGoal(
                "汇总 {{now}} 之前的新增工单", now);

        assertTrue(rendered.contains("2026-10-03 09:00"));
        assertTrue(rendered.endsWith("新增工单"));
    }

    @Test
    void shouldKeepGoalUnchangedWithoutVariables() {
        assertEquals("执行每日巡检",
                TriggerScheduleCalculator.renderGoal("执行每日巡检", ZonedDateTime.now(ZONE)));
    }
}

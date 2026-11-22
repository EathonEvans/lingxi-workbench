package com.lingxi.workbench.application.runtime.service.trigger;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 触发器调度时间计算：cron 求值、静默时段顺延。
 *
 * <p>cron 使用 Spring 6 段表达式（秒 分 时 日 月 周），由服务端
 * {@link CronExpression} 求值，避免引入 Quartz 等额外中间件。</p>
 */
public final class TriggerScheduleCalculator {

    private static final DateTimeFormatter QUIET_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /** 目标模板中的当前时间变量。 */
    public static final String NOW_VARIABLE = "{{now}}";
    private static final DateTimeFormatter NOW_VARIABLE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TriggerScheduleCalculator() {
    }

    /** 校验 cron 表达式合法；非法时抛出 IllegalArgumentException。 */
    public static CronExpression parseCron(String cronExpr) {
        return CronExpression.parse(cronExpr.trim());
    }

    /** 按触发器时区计算指定时间之后的下一次触发时间。 */
    public static ZonedDateTime nextFire(String cronExpr, String timezone, ZonedDateTime after) {
        CronExpression cron = parseCron(cronExpr);
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime next = cron.next(after.withZoneSameInstant(zone));
        if (next == null) {
            throw new IllegalArgumentException("cron表达式没有后续触发时间：" + cronExpr);
        }
        return next;
    }

    /**
     * 落点在静默时段内时顺延到窗口结束时刻；不在窗口内或未配置静默时原样返回。
     *
     * <p>静默窗口格式 HH:mm-HH:mm，支持跨零点（如 22:00-08:00）。</p>
     */
    public static ZonedDateTime shiftIfQuietHours(ZonedDateTime fireTime, String quietHours) {
        if (quietHours == null || quietHours.isBlank()) {
            return fireTime;
        }
        String[] parts = quietHours.trim().split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("静默时段格式不正确：" + quietHours);
        }
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(parts[0].trim(), QUIET_TIME_FORMAT);
            end = LocalTime.parse(parts[1].trim(), QUIET_TIME_FORMAT);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("静默时段格式不正确：" + quietHours, exception);
        }
        LocalTime time = fireTime.toLocalDateTime().toLocalTime();
        boolean inWindow = start.isBefore(end)
                ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
        if (!inWindow) {
            return fireTime;
        }
        // 顺延候选为“落点当日 + 窗口结束”；若已早于落点（跨零点窗口的凌晨落点以外场景）则顺延一天。
        LocalDateTime candidate = fireTime.toLocalDate().atTime(end);
        if (!candidate.isAfter(fireTime.toLocalDateTime())) {
            candidate = candidate.plusDays(1);
        }
        return candidate.atZone(fireTime.getZone());
    }

    /** 渲染目标模板中的有限变量；当前仅支持 {{now}} 当前时间。 */
    public static String renderGoal(String goalTemplate, ZonedDateTime now) {
        String rendered = goalTemplate;
        if (rendered.contains(NOW_VARIABLE)) {
            rendered = rendered.replace(NOW_VARIABLE,
                    now.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().format(NOW_VARIABLE_FORMAT));
        }
        return rendered.trim();
    }
}

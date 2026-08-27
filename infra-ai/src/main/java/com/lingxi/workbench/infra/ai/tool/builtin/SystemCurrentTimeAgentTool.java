package com.lingxi.workbench.infra.ai.tool.builtin;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Map;

import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DYNAMIC_READ_ONLY;

/**
 * 查询指定时区当前时间的只读内置工具。
 *
 * <p>这里的定义用于执行器启动校验；正式调用时，应用运行端会使用 Tool 管理中的
 * 名称、描述和参数 Schema 装饰该回调。业务执行代码不依赖管理端模块。</p>
 */
@Component
public class SystemCurrentTimeAgentTool implements AgentTool {


    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .code("system_current_time")
                .displayName("查询当前时间")
                .description("查询指定 IANA 时区的当前日期、时间和时区；用户询问当前时间或日期时使用。")
                .executionMode(DYNAMIC_READ_ONLY)
                .build();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "zoneId", Map.of("type", "string", "description", "IANA时区，例如Asia/Shanghai")),
                "additionalProperties", false);
    }

    @Override
    public String execute(AgentToolRequest request) {
        CurrentTimeInput input = JSON.parseObject(request.input(), CurrentTimeInput.class);
        return JSON.toJSONString(execute(input));
    }

    private CurrentTimeOutput execute(CurrentTimeInput input) {
        ZoneId zoneId = resolveZoneId(input.zoneId());
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        return new CurrentTimeOutput(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), now.toLocalDate().toString(), zoneId.getId());
    }

    private static ZoneId resolveZoneId(String requestedZoneId) {
        if (Objects.isNull(requestedZoneId) || requestedZoneId.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(requestedZoneId.trim());
        } catch (DateTimeException exception) {
            throw new AgentToolException("不支持的IANA时区：" + requestedZoneId, exception);
        }
    }

    /**
     * 当前时间查询参数。
     *
     * @param zoneId IANA 时区，例如 Asia/Shanghai；不传时使用系统时区
     */
    public record CurrentTimeInput(
            String zoneId) {
    }

    /**
     * 当前时间查询结果。
     *
     * @param currentTime 当前时间的时分秒部分
     * @param currentDate 当前日期的年月日部分
     * @param zoneId      实际使用的 IANA 时区
     */
    public record CurrentTimeOutput(String currentTime, String currentDate, String zoneId) {
    }
}

package com.lingxi.workbench.infra.ai.tool.builtin;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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
    public ToolCallback callback() {
        AgentToolDefinition definition = definition();
        return FunctionToolCallback.builder(definition.getCode(), this::execute)
                .description(definition.getDescription())
                .inputType(CurrentTimeInput.class)
                .build();
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
     * Spring AI 根据参数类型自动生成 Function parameters JSON Schema。
     */
    public record CurrentTimeInput(
            @ToolParam(description = "IANA时区，例如Asia/Shanghai；不传时使用系统时区", required = false) String zoneId) {
    }

    /**
     * Spring AI 将返回对象自动序列化为 Tool 消息 content。
     */
    public record CurrentTimeOutput(String currentTime, String currentDate, String zoneId) {
    }
}

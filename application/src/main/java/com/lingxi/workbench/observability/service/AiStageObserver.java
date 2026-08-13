package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 在当前 Trace 下创建稳定的 Application Chat 阶段 span。
 */
@Component
@RequiredArgsConstructor
public class AiStageObserver {

    private final ObservationRegistry observationRegistry;

    /**
     * 观测执行
     *
     * @param stage 步骤
     * @param action 操作
     * @return 操作结果
     * @param <T> 操作结果类型
     */
    public <T> T observe(AiObservationStage stage, Supplier<T> action) {
        return observe(stage, null, action);
    }

    /**
     * 观测执行，并在 span 关闭前按执行结果记录高基数属性（如路由候选与相似度）。
     *
     * @param stage          步骤
     * @param resultRecorder 结果属性记录器，在 span 内、stop 前执行
     * @param action         操作
     * @return 操作结果
     * @param <T> 操作结果类型
     */
    public <T> T observe(AiObservationStage stage, BiConsumer<Observation, T> resultRecorder, Supplier<T> action) {
        Observation observation = Observation.createNotStarted("lingxi.ai.stage", observationRegistry)
                .contextualName(stage.getCode().toLowerCase())
                .lowCardinalityKeyValue("lingxi.stage", stage.getCode());
        return observation.observe(() -> {
            T result = action.get();
            if (Objects.nonNull(resultRecorder)) {
                resultRecorder.accept(observation, result);
            }
            return result;
        });
    }

    public void observe(AiObservationStage stage, Runnable action) {
        observe(stage, null, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 为最终调度目标建立带 executionId/runId 的子链路。
     */
    public <T> T observeExecution(String executionId, String executionMode, Supplier<T> action) {
        Observation observation = Observation.createNotStarted("lingxi.ai.execution", observationRegistry)
                .contextualName("application-execution")
                .highCardinalityKeyValue("lingxi.run.id", executionId)
                .lowCardinalityKeyValue("lingxi.execution.mode", executionMode);
        return observation.observe(action);
    }
}

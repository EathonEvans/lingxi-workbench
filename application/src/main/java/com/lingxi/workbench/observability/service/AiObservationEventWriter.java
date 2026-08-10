package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import com.lingxi.workbench.observability.repo.AiObservationEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.lingxi.workbench.observability.config.AiObservationAsyncConfiguration.EXECUTOR_NAME;

/** 异步写入观测事件，观测存储故障不能反向破坏对话主链路。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiObservationEventWriter {

    private final AiObservationEventRepo eventRepo;

    @Async(EXECUTOR_NAME)
    public void append(AiObservationEventEntity event) {
        try {
            eventRepo.append(event);
        } catch (RuntimeException exception) {
            log.warn("Agent观测事件落库失败，traceId={}，eventType={}", event.getTraceId(), event.getEventType(), exception);
        }
    }
}

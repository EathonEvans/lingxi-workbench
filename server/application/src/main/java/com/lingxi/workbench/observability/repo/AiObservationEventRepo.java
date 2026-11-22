package com.lingxi.workbench.observability.repo;

import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import com.lingxi.workbench.observability.repo.mapper.AiObservationEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Agent 关键链路事件的追加式持久化仓库。 */
@Repository
@RequiredArgsConstructor
public class AiObservationEventRepo {

    private final AiObservationEventMapper mapper;

    public void append(AiObservationEventEntity event) {
        mapper.insert(event);
    }
}

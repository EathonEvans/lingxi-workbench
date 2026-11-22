package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 到期触发的本地事务：抢占 next_fire_at 与写入触发记录在同一事务内原子提交。
 */
@Service
@RequiredArgsConstructor
public class TriggerScanTxService {

    private final TriggerRepo triggerRepo;
    private final TriggerFireRepo fireRepo;

    /**
     * 抢占式推进下次触发时间并写入 FIRED 触发记录。
     *
     * <p>CAS 抢占失败（其他实例已处理）时直接返回 false，不写触发记录。</p>
     *
     * @return 触发记录主键；抢占失败返回 null
     */
    @Transactional
    public Long claimAndRecordFire(TriggerEntity trigger, OffsetDateTime nextFireAt, OffsetDateTime now) {
        if (!claim(trigger, nextFireAt)) {
            return null;
        }
        TriggerFireEntity fire = newFire(trigger, now, TriggerFireStatus.FIRED, null, null);
        fireRepo.insert(fire);
        return fire.getId();
    }

    /**
     * 抢占后写入跳过记录；跳过同样推进 next_fire_at，避免同一到期时刻被反复扫描。
     */
    @Transactional
    public void recordSkipped(TriggerEntity trigger, OffsetDateTime nextFireAt, OffsetDateTime now,
                              TriggerFireStatus status, String skipReason, String errorMessage) {
        if (!claim(trigger, nextFireAt)) {
            return;
        }
        fireRepo.insert(newFire(trigger, now, status, skipReason, errorMessage));
    }

    private boolean claim(TriggerEntity trigger, OffsetDateTime nextFireAt) {
        return triggerRepo.claimNextFire(trigger.getId(), trigger.getNextFireAt(), nextFireAt);
    }

    private static TriggerFireEntity newFire(TriggerEntity trigger, OffsetDateTime now, TriggerFireStatus status,
                                             String skipReason, String errorMessage) {
        TriggerFireEntity fire = new TriggerFireEntity();
        fire.setTriggerId(trigger.getId());
        fire.setUserId(trigger.getUserId());
        fire.setPlannedAt(trigger.getNextFireAt());
        fire.setFiredAt(status == TriggerFireStatus.FIRED ? now : null);
        fire.setStatus(status);
        fire.setSkipReason(skipReason);
        fire.setErrorMessage(errorMessage);
        return fire;
    }
}

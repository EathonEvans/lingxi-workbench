package com.lingxi.workbench.application.runtime.service.trigger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 到期触发投递消息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TriggerFireMessage {

    /** 触发记录主键，消费侧幂等抢占的依据。 */
    private Long fireId;

    /** 归属触发器主键。 */
    private Long triggerId;
}

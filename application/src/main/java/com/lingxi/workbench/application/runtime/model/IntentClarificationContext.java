package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** 澄清会话持久化的候选、目标意图和槽位进度。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentClarificationContext {
    private String originalQuery;
    private List<String> candidateCodes;
    private String intentCode;
    private List<String> missingSlotCodes;
    private Map<String, String> collectedSlots;
}

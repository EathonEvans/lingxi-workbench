package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * V1 支持的意图澄清类型。
 *
 * <p>澄清会话由 {@code IntentRoutingSessionEntity} 持久化，
 * 锁定当前 ApplicationVersion 和 IntentTreeVersion，
 * 确保澄清过程中资源发布新版本不会导致选项或执行目标漂移。</p>
 *
 * <ul>
 *   <li><b>INTENT_DISAMBIGUATION</b> — 多个候选叶子意图相似度接近
 *       （Top-1 与 Top-2 分差小于 ambiguityMargin），
 *       需要用户从候选中明确选择一个。</li>
 *   <li><b>MISSING_SLOT</b> — 目标意图已明确，但缺少必填槽位参数，
 *       需要用户逐项补充。最多 2 轮澄清，超限后进入失败兜底。</li>
 * </ul>
 *
 * <p>注意：V1 暂不实现 OPERATION_CONFIRMATION（高风险操作确认），
 * 待 M3 阶段引入风险评分和 LLM 复判后补充。</p>
 */
@Getter
@RequiredArgsConstructor
public enum IntentClarificationType {

    /**
     * 歧义消解：多个候选接近，需用户选择
     */
    INTENT_DISAMBIGUATION("INTENT_DISAMBIGUATION", "意图歧义消解"),

    /**
     * 缺少槽位：目标明确但缺必填参数
     */
    MISSING_SLOT("MISSING_SLOT", "必填槽位补充");

    @EnumValue
    private final String code;
    private final String describe;
}

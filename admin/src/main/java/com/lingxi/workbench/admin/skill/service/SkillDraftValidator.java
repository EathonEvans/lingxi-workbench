package com.lingxi.workbench.admin.skill.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.skill.common.enums.SkillIssueSeverity;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.model.config.SkillScanFinding;
import com.lingxi.workbench.admin.skill.model.config.SkillScanReport;
import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationIssueVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationResultVO;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static com.lingxi.workbench.infra.ai.skill.common.constants.SkillSchemaConstants.SKILL_SCHEMA_VERSION;

/** Skill 发布前业务校验和确定性静态安全扫描。 */
@Component
@RequiredArgsConstructor
public class SkillDraftValidator {

    public static final String SCANNER_VERSION = "skill-static-v1";

    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]{16,}");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{16,}\\b");
    private static final Pattern PROMPT_OVERRIDE = Pattern.compile(
            "(?is)(ignore\\s+(all\\s+)?previous\\s+(instructions?|prompts?)|"
                    + "忽略.{0,16}(系统|平台|之前).{0,16}(规则|指令|提示词))");
    private static final Pattern EXTERNAL_ENDPOINT = Pattern.compile("(?i)https?://|mcp://|webhook");

    private final ToolService toolService;

    public SkillValidationResultVO validate(SkillEntity skill, SkillDraftEntity draft) {
        List<SkillValidationIssueVO> issues = new ArrayList<>();
        if (skill.getStatus() != SkillLifecycleStatus.ACTIVE) {
            issues.add(error("SKILL_DISABLED", "status", "停用的Skill不能发布"));
        }
        if (skill.getSecurityStatus() != SkillSecurityStatus.NORMAL) {
            issues.add(error("SKILL_SECURITY_BLOCKED", "securityStatus", "当前安全状态不允许发布"));
        }
        if (!Objects.equals(draft.getSchemaVersion(), SKILL_SCHEMA_VERSION)) {
            issues.add(error("SCHEMA_UNSUPPORTED", "schemaVersion", "Skill草稿协议版本不受支持"));
        }
        if (CharSequenceUtil.isBlank(draft.getInstructions())) {
            issues.add(error("INSTRUCTIONS_REQUIRED", "instructions", "Skill指令不能为空"));
        }
        if (Objects.isNull(draft.getActivationMode())) {
            issues.add(error("ACTIVATION_MODE_REQUIRED", "activationMode", "请选择Skill激活方式"));
        }
        if (skill.getSourceType() == SkillSourceType.THIRD_PARTY_IMPORTED
                && draft.getActivationMode() != SkillActivationMode.EXPLICIT_ONLY) {
            issues.add(error(
                    "THIRD_PARTY_EXPLICIT_ONLY", "activationMode", "第三方Skill只能由用户显式启用"));
        }
        if (draft.getActivationMode() == SkillActivationMode.ALWAYS_ON
                && skill.getSourceType() != SkillSourceType.SYSTEM_PRESET) {
            issues.add(error(
                    "ALWAYS_ON_SYSTEM_ONLY", "activationMode", "仅平台预设Skill允许每轮自动启用"));
        }

        List<String> toolCodes = Objects.requireNonNullElse(draft.getRequestedToolCodes(), List.of());
        Set<String> distinctCodes = new HashSet<>(toolCodes);
        if (distinctCodes.size() != toolCodes.size()) {
            issues.add(error("TOOL_CODE_DUPLICATED", "requestedToolCodes", "Tool请求列表存在重复编码"));
        }
        Set<String> activeCodes = toolService.findActiveCodes(toolCodes);
        toolCodes.stream()
                .filter(code -> !activeCodes.contains(code))
                .forEach(code -> issues.add(error(
                        "TOOL_UNAVAILABLE", "requestedToolCodes", "Tool不可用：" + code)));
        issues.addAll(scanInstructions(draft.getInstructions()));

        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == SkillIssueSeverity.ERROR);
        return new SkillValidationResultVO(valid, issues);
    }

    /** 发布快照保留扫描规则版本和发现，不保存命中的敏感原文。 */
    public SkillScanReport scanReport(String instructions) {
        List<SkillScanFinding> findings = scanInstructions(instructions).stream()
                .map(issue -> new SkillScanFinding(issue.severity(), issue.code(), issue.message()))
                .toList();
        return new SkillScanReport(SCANNER_VERSION, findings);
    }

    private static List<SkillValidationIssueVO> scanInstructions(String instructions) {
        if (CharSequenceUtil.isBlank(instructions)) {
            return List.of();
        }
        List<SkillValidationIssueVO> issues = new ArrayList<>();
        if (PRIVATE_KEY.matcher(instructions).find()) {
            issues.add(error("SECRET_PRIVATE_KEY", "instructions", "Skill指令疑似包含私钥"));
        }
        if (BEARER_TOKEN.matcher(instructions).find() || API_KEY.matcher(instructions).find()) {
            issues.add(error("SECRET_TOKEN", "instructions", "Skill指令疑似包含访问密钥"));
        }
        if (PROMPT_OVERRIDE.matcher(instructions).find()) {
            issues.add(error("PROMPT_OVERRIDE", "instructions", "Skill指令包含覆盖平台规则的高风险表达"));
        }
        if (EXTERNAL_ENDPOINT.matcher(instructions).find()) {
            issues.add(warning(
                    "EXTERNAL_ENDPOINT", "instructions", "Skill指令包含外部地址，发布前请确认数据边界"));
        }
        return issues;
    }

    private static SkillValidationIssueVO error(String code, String field, String message) {
        return new SkillValidationIssueVO(SkillIssueSeverity.ERROR, code, field, message);
    }

    private static SkillValidationIssueVO warning(String code, String field, String message) {
        return new SkillValidationIssueVO(SkillIssueSeverity.WARNING, code, field, message);
    }
}

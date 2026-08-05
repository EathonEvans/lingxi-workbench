package com.lingxi.workbench.admin.intent.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationIssueVO;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 意图树结构、语义样本、槽位和召回策略的发布校验器。 */
@Component
public class IntentTreeDefinitionValidator {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_NODE_COUNT = 100;
    private static final int MAX_DEPTH = 3;
    private static final int MIN_POSITIVE_EXAMPLES = 2;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{1,63}$");

    /** 校验意图树定义，返回可以直接定位到节点或字段的问题列表。 */
    public List<IntentTreeValidationIssueVO> validate(IntentTreeDefinition definition) {
        List<IntentTreeValidationIssueVO> issues = new ArrayList<>();
        if (Objects.isNull(definition)) {
            issues.add(issue("DEFINITION_REQUIRED", "definition", "意图树定义不能为空"));
            return issues;
        }
        if (definition.schemaVersion() != SCHEMA_VERSION) {
            issues.add(issue("SCHEMA_VERSION_UNSUPPORTED", "definition.schemaVersion", "当前仅支持Schema V1"));
        }
        validateRoutingPolicy(definition.routingPolicy(), issues);
        validateNodes(definition.nodes(), issues);
        return issues;
    }

    private static void validateRoutingPolicy(IntentRoutingPolicy policy, List<IntentTreeValidationIssueVO> issues) {
        if (Objects.isNull(policy)) {
            issues.add(issue("ROUTING_POLICY_REQUIRED", "definition.routingPolicy", "请配置候选召回策略"));
            return;
        }
        if (policy.topK() < 1 || policy.topK() > 20) {
            issues.add(issue("TOP_K_INVALID", "definition.routingPolicy.topK", "Top-K必须在1到20之间"));
        }
        validateProbability(policy.recallThreshold(), "RECALL_THRESHOLD_INVALID", "definition.routingPolicy.recallThreshold", "召回阈值", issues);
        validateProbability(policy.ambiguityMargin(), "AMBIGUITY_MARGIN_INVALID", "definition.routingPolicy.ambiguityMargin", "歧义分差", issues);
    }

    private static void validateProbability(BigDecimal value, String code, String field, String label, List<IntentTreeValidationIssueVO> issues) {
        if (Objects.isNull(value) || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            issues.add(issue(code, field, label + "必须在0到1之间"));
        }
    }

    private static void validateNodes(List<IntentNodeDefinition> nodes, List<IntentTreeValidationIssueVO> issues) {
        if (Objects.isNull(nodes) || nodes.isEmpty()) {
            issues.add(issue("NODES_REQUIRED", "definition.nodes", "意图树至少需要根节点和一个叶子意图"));
            return;
        }
        if (nodes.size() > MAX_NODE_COUNT) {
            issues.add(issue("NODE_COUNT_EXCEEDED", "definition.nodes", "单棵意图树最多允许100个节点"));
        }

        Map<String, IntentNodeDefinition> nodesById = new HashMap<>();
        Set<String> codes = new HashSet<>();
        List<IntentNodeDefinition> roots = new ArrayList<>();
        int enabledLeafCount = 0;
        for (int index = 0; index < nodes.size(); index++) {
            IntentNodeDefinition node = nodes.get(index);
            String field = "definition.nodes[" + index + "]";
            if (Objects.isNull(node)) {
                issues.add(issue("NODE_REQUIRED", field, "节点不能为空"));
                continue;
            }
            validateNodeFields(node, field, codes, issues);
            if (CharSequenceUtil.isNotBlank(node.getId()) && Objects.nonNull(nodesById.putIfAbsent(node.getId(), node))) {
                issues.add(issue("NODE_ID_DUPLICATED", field + ".id", "节点ID不能重复"));
            }
            if (CharSequenceUtil.isBlank(node.getParentId())) {
                roots.add(node);
            }
            if (node.isEnabled() && node.getNodeType() == IntentNodeType.LEAF) {
                enabledLeafCount++;
                validateLeaf(node, field, issues);
            }
        }
        if (roots.size() != 1) {
            issues.add(issue("ROOT_COUNT_INVALID", "definition.nodes", "意图树必须且只能有一个根节点"));
        } else if (roots.getFirst().getNodeType() != IntentNodeType.ROUTER) {
            issues.add(issue("ROOT_TYPE_INVALID", "definition.nodes", "根节点必须是分类节点"));
        }
        if (enabledLeafCount == 0) {
            issues.add(issue("ENABLED_LEAF_REQUIRED", "definition.nodes", "至少需要一个启用的叶子意图"));
        }
        validateRelations(nodes, nodesById, roots, issues);
    }

    private static void validateNodeFields(IntentNodeDefinition node, String field, Set<String> codes, List<IntentTreeValidationIssueVO> issues) {
        if (CharSequenceUtil.isBlank(node.getId())) {
            issues.add(issue("NODE_ID_REQUIRED", field + ".id", "节点ID不能为空"));
        }
        if (!isValidCode(node.getCode())) {
            issues.add(issue("NODE_CODE_INVALID", field + ".code", "节点编码格式不正确"));
        } else if (!codes.add(node.getCode())) {
            issues.add(issue("NODE_CODE_DUPLICATED", field + ".code", "节点编码不能重复"));
        }
        if (CharSequenceUtil.isBlank(node.getName())) {
            issues.add(issue("NODE_NAME_REQUIRED", field + ".name", "节点名称不能为空"));
        }
        if (Objects.isNull(node.getNodeType())) {
            issues.add(issue("NODE_TYPE_REQUIRED", field + ".nodeType", "请选择节点类型"));
        }
        if (Objects.nonNull(node.getPriority()) && node.getPriority() < 0) {
            issues.add(issue("NODE_PRIORITY_INVALID", field + ".priority", "节点优先级不能小于0"));
        }
    }

    private static void validateLeaf(IntentNodeDefinition node, String field, List<IntentTreeValidationIssueVO> issues) {
        if (CharSequenceUtil.isBlank(node.getDescription())) {
            issues.add(issue("LEAF_DESCRIPTION_REQUIRED", field + ".description", "启用的叶子意图必须填写语义描述"));
        }
        List<String> positiveExamples = Objects.requireNonNullElse(node.getPositiveExamples(), List.of());
        long validExampleCount = positiveExamples.stream().filter(CharSequenceUtil::isNotBlank).distinct().count();
        if (validExampleCount < MIN_POSITIVE_EXAMPLES) {
            issues.add(issue("POSITIVE_EXAMPLES_INSUFFICIENT", field + ".positiveExamples", "启用的叶子意图至少需要2条不同的正例"));
        }
        validateSlots(node, field, issues);
    }

    private static void validateSlots(IntentNodeDefinition node, String field, List<IntentTreeValidationIssueVO> issues) {
        List<IntentSlotDefinition> slots = Objects.requireNonNullElse(node.getSlots(), List.of());
        Set<String> slotCodes = new HashSet<>();
        boolean hasRequiredSlot = false;
        for (int index = 0; index < slots.size(); index++) {
            IntentSlotDefinition slot = slots.get(index);
            String slotField = field + ".slots[" + index + "]";
            if (Objects.isNull(slot)) {
                issues.add(issue("SLOT_REQUIRED", slotField, "槽位不能为空"));
                continue;
            }
            if (!isValidCode(slot.getCode())) {
                issues.add(issue("SLOT_CODE_INVALID", slotField + ".code", "槽位编码格式不正确"));
            } else if (!slotCodes.add(slot.getCode())) {
                issues.add(issue("SLOT_CODE_DUPLICATED", slotField + ".code", "同一意图内槽位编码不能重复"));
            }
            if (CharSequenceUtil.isBlank(slot.getName())) {
                issues.add(issue("SLOT_NAME_REQUIRED", slotField + ".name", "槽位名称不能为空"));
            }
            if (Objects.isNull(slot.getValueType())) {
                issues.add(issue("SLOT_TYPE_REQUIRED", slotField + ".valueType", "请选择槽位类型"));
            }
            hasRequiredSlot = hasRequiredSlot || slot.isRequired();
        }
        if (hasRequiredSlot && CharSequenceUtil.isBlank(node.getClarificationQuestion())) {
            issues.add(issue("CLARIFICATION_REQUIRED", field + ".clarificationQuestion", "存在必填槽位时必须配置澄清问题"));
        }
        long requiredSlotCount = slots.stream()
                .filter(Objects::nonNull)
                .filter(IntentSlotDefinition::isRequired)
                .count();
        if (requiredSlotCount > 2) {
            issues.add(issue("REQUIRED_SLOT_COUNT_EXCEEDED", field + ".slots", "V1单个意图最多支持2个必填槽位"));
        }
    }

    private static void validateRelations(List<IntentNodeDefinition> nodes, Map<String, IntentNodeDefinition> nodesById, List<IntentNodeDefinition> roots, List<IntentTreeValidationIssueVO> issues) {
        for (IntentNodeDefinition node : nodes) {
            if (Objects.isNull(node) || CharSequenceUtil.isBlank(node.getParentId())) {
                continue;
            }
            IntentNodeDefinition parent = nodesById.get(node.getParentId());
            if (Objects.isNull(parent)) {
                issues.add(issue("PARENT_NOT_FOUND", node.getId(), "父节点不存在"));
                continue;
            }
            if (parent.getNodeType() != IntentNodeType.ROUTER) {
                issues.add(issue("LEAF_HAS_CHILD", node.getId(), "叶子意图不能包含子节点"));
            }
        }
        if (roots.size() != 1) {
            return;
        }
        for (IntentNodeDefinition node : nodes) {
            if (Objects.isNull(node) || CharSequenceUtil.isBlank(node.getId())) {
                continue;
            }
            validatePath(node, roots.getFirst().getId(), nodesById, issues);
        }
    }

    private static void validatePath(IntentNodeDefinition node, String rootId, Map<String, IntentNodeDefinition> nodesById, List<IntentTreeValidationIssueVO> issues) {
        Set<String> visited = new HashSet<>();
        IntentNodeDefinition current = node;
        int depth = 1;
        while (Objects.nonNull(current) && CharSequenceUtil.isNotBlank(current.getParentId())) {
            if (!visited.add(current.getId())) {
                issues.add(issue("TREE_CYCLE", node.getId(), "节点关系不能形成循环"));
                return;
            }
            depth++;
            current = nodesById.get(current.getParentId());
        }
        if (Objects.isNull(current) || !Objects.equals(current.getId(), rootId)) {
            issues.add(issue("NODE_UNREACHABLE", node.getId(), "节点必须从根节点可达"));
        }
        if (depth > MAX_DEPTH) {
            issues.add(issue("TREE_DEPTH_EXCEEDED", node.getId(), "意图树最大深度为3层"));
        }
    }

    private static boolean isValidCode(String code) {
        return CharSequenceUtil.isNotBlank(code) && CODE_PATTERN.matcher(code).matches();
    }

    private static IntentTreeValidationIssueVO issue(String code, String field, String message) {
        return new IntentTreeValidationIssueVO(code, field, message);
    }
}

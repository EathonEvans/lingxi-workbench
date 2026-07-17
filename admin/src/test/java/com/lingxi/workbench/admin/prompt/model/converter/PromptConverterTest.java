package com.lingxi.workbench.admin.prompt.model.converter;

import com.lingxi.workbench.admin.prompt.common.enums.PromptRole;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableOverflowStrategy;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableScope;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableSource;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVariableDefinition;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVersionVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptConverterTest {

    private final PromptConverter converter = Mappers.getMapper(PromptConverter.class);

    @Test
    void shouldConvertStrongTypedVariableDefinitionsWithoutJsonParsing() {
        PromptVariableDefinition definition =
                new PromptVariableDefinition(
                        "input.language", "回答语言", PromptVariableType.STRING,
                        PromptVariableScope.INPUT, PromptVariableSource.USER_INPUT,
                        true, "中文", "回答语言", "中文", 100, null, 50,
                        PromptVariableOverflowStrategy.REJECT, false, true);
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(2077574496283791361L);
        entity.setVariables(List.of(definition));
        entity.setStatus(PromptVersionStatus.DRAFT);

        PromptVersionVO result = converter.toVersionVO(entity);

        assertEquals("2077574496283791361", result.id());
        assertEquals("input.language", result.variables().getFirst().name());
        assertEquals("中文", result.variables().getFirst().defaultValue());
    }

    @Test
    void shouldConvertPromptRoleToResponse() {
        PromptEntity entity = new PromptEntity();
        entity.setId(1L);
        entity.setRole(PromptRole.USER);

        assertEquals(PromptRole.USER, converter.toPromptVO(entity, null, null).role());
        assertEquals(
                PromptRole.USER,
                converter.toDetailVO(entity, null, null, null, "", List.of(), null).role());
    }

    @Test
    void shouldKeepCollectionFieldsNonNull() {
        assertEquals(List.of(), converter.toVariableVOs(null));
        assertEquals(List.of(), converter.toVariableRequests(null));
        assertEquals(List.of(), converter.toDefinitions(null));
    }

    @Test
    void shouldRoundTripRequestAndEmbeddedDefinition() {
        PromptVariableRequest request =
                new PromptVariableRequest(
                        "agent.name", "Agent名称", PromptVariableType.STRING,
                        PromptVariableScope.AGENT, PromptVariableSource.AGENT_CONFIG,
                        true, null, "Agent名称", "灵析助手", 100, null, 50,
                        PromptVariableOverflowStrategy.REJECT, false, false);

        PromptVariableDefinition definition = converter.toDefinition(request);
        PromptVariableRequest restored = converter.toVariableRequest(definition);

        assertEquals(request, restored);
    }
}

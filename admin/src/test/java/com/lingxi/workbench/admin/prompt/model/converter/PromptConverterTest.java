package com.lingxi.workbench.admin.prompt.model.converter;

import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
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
                PromptVariableDefinition.builder()
                        .name("input.language")
                        .type(PromptVariableType.STRING)
                        .required(true)
                        .description("回答语言")
                        .example("中文")
                        .build();
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(2077574496283791361L);
        entity.setVariables(List.of(definition));
        entity.setStatus(PromptVersionStatus.DRAFT);

        PromptVersionVO result = converter.toVersionVO(entity);

        assertEquals("2077574496283791361", result.getId());
        assertEquals("input.language", result.getVariables().getFirst().getName());
        assertEquals("中文", result.getVariables().getFirst().getExample());
    }

    @Test
    void shouldConvertPromptRoleToResponse() {
        PromptEntity entity = new PromptEntity();
        entity.setId(1L);
        entity.setRole(PromptRole.USER);

        assertEquals(PromptRole.USER, converter.toPromptVO(entity, null, null).getRole());
        assertEquals(
                PromptRole.USER,
                converter.toDetailVO(entity, null, null, null, "", List.of(), null).getRole());
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
                PromptVariableRequest.builder()
                        .name("agent.name")
                        .type(PromptVariableType.STRING)
                        .required(true)
                        .description("Agent名称")
                        .example("灵析助手")
                        .build();

        PromptVariableDefinition definition = converter.toDefinition(request);
        PromptVariableRequest restored = converter.toVariableRequest(definition);

        assertEquals(request, restored);
    }
}

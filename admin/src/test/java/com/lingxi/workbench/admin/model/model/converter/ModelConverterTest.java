package com.lingxi.workbench.admin.model.model.converter;

import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.model.vo.ModelInstanceVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelConverterTest {

    private final ModelConverter converter = Mappers.getMapper(ModelConverter.class);

    @Test
    void shouldConvertLargeIdsAndJsonArraysWithoutLosingInformation() {
        ModelInstanceEntity entity = new ModelInstanceEntity();
        entity.setId(2077574496283791361L);
        entity.setProviderId(2077574496283791362L);
        entity.setCredentialId(2077574496283791363L);
        entity.setModelType(ModelType.CHAT);
        entity.setCapabilities(List.of(ModelCapability.STREAMING, ModelCapability.TOOL_CALLING));
        entity.setStopSequences(List.of("END"));

        ModelInstanceVO result = converter.toInstanceVO(entity);

        assertEquals("2077574496283791361", result.getId());
        assertEquals("2077574496283791362", result.getProviderId());
        assertEquals("2077574496283791363", result.getCredentialId());
        assertEquals("CHAT", result.getModelType());
        assertEquals(List.of("STREAMING", "TOOL_CALLING"), result.getCapabilities());
        assertEquals(List.of("END"), result.getStopSequences());
    }

}

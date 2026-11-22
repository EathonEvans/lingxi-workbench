package com.lingxi.workbench.admin.domain.model.converter;

import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.model.vo.DomainVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainConverterTest {

    private final DomainConverter converter = Mappers.getMapper(DomainConverter.class);

    @Test
    void shouldExposeDomainIdsAsStrings() {
        DomainEntity entity = new DomainEntity();
        entity.setId(12L);
        entity.setParentId(3L);
        entity.setRootId(1L);

        DomainVO domain = converter.toVO(entity);

        assertEquals("12", domain.getId());
        assertEquals("3", domain.getParentId());
        assertEquals("1", domain.getRootId());
    }
}

package com.lingxi.workbench.admin.domain.service;

import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.model.request.CreateDomainRequest;
import com.lingxi.workbench.admin.domain.model.request.UpdateDomainRequest;
import com.lingxi.workbench.admin.domain.model.vo.DomainVO;

import java.util.List;

/** 领域目录管理与归属校验。 */
public interface DomainService {
    List<DomainVO> listTree();

    DomainVO create(CreateDomainRequest request, Long operatorId);

    DomainVO update(Long id, UpdateDomainRequest request, Long operatorId);

    void delete(Long id);

    DomainEntity requireActive(Long id);

    DomainEntity requireRoot(Long id);

    boolean belongsTo(Long domainId, Long rootId);
}

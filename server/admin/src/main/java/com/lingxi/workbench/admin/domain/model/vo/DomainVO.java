package com.lingxi.workbench.admin.domain.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 领域树节点视图。 */
@Data
@Builder
public class DomainVO {
    private String id;
    private String parentId;
    private String rootId;
    private String name;
    private String code;
    private String description;
    private Integer depth;
    private Integer sortOrder;
    private EnableStatus status;
    private List<DomainVO> children;
}

package com.lingxi.workbench.admin.model.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型供应商数据库实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_model_provider")
public class ModelProviderEntity extends AuditTimeEntity {
    /** 供应商主键。 */
    private Long id;
    /** 供应商名称。 */
    private String name;
    /** 供应商编码，全局唯一，仅支持小写字母、数字、中划线和下划线。 */
    private String code;
    /** 协议类型，当前仅支持 OPENAI_COMPATIBLE。 */
    private String protocol;
    /** 接口基础地址。 */
    private String baseUrl;
    /** 启用状态：ACTIVE / DISABLED。 */
    private EnableStatus status;
    /** 备注说明。 */
    private String remark;
    /** 创建人用户主键。 */
    private Long createdBy;
    /** 更新人用户主键。 */
    private Long updatedBy;
}

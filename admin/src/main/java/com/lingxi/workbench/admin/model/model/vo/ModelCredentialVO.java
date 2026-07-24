package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.model.common.enums.ModelCredentialStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 模型密钥脱敏响应，所有关联主键均使用字符串传输。
 *
 * @param id         密钥主键
 * @param providerId 所属供应商主键
 * @param name       密钥名称
 * @param secretHint 密钥脱敏提示
 * @param status     密钥状态
 * @param rotatedAt  最近一次轮换时间
 * @param updatedAt  更新时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelCredentialVO {

    private String id;

    private String providerId;

    private String name;

    private String secretHint;

    private ModelCredentialStatus status;

    private OffsetDateTime rotatedAt;

    private OffsetDateTime updatedAt;
}

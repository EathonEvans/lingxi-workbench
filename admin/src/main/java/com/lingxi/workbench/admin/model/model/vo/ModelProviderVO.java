package com.lingxi.workbench.admin.model.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 模型供应商响应，主键使用字符串避免 JavaScript 大整数精度丢失。
 *
 * @param id        供应商主键，使用字符串避免 JavaScript 大整数精度丢失
 * @param name      供应商名称
 * @param code      供应商编码
 * @param protocol  协议类型
 * @param baseUrl   接口基础地址
 * @param status    启用状态
 * @param remark    备注说明
 * @param updatedAt 更新时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelProviderVO {

    private String id;

    private String name;

    private String code;

    private String protocol;

    private String baseUrl;

    private EnableStatus status;

    private String remark;

    private OffsetDateTime updatedAt;
}

package com.lingxi.workbench.admin.knowledge.service;

import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeBasePageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeBaseRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeBaseVO;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.SaveKnowledgeAccessRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessOptionVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeAccessVO;
import java.util.List;

/**
 * 知识库管理业务。
 */
public interface KnowledgeBaseService {
    /** 分页查询授权用户选项。 */
    PageResult<KnowledgeAccessOptionVO> userOptions(SysUserPageQuery query);
    /** 查询授权角色选项。 */
    List<KnowledgeAccessOptionVO> roleOptions();
    /** 查询直属部门授权选项，展示完整组织路径。 */
    List<KnowledgeAccessOptionVO> departmentOptions();

    /** 分页查询知识库。 */
    PageResult<KnowledgeBaseVO> page(KnowledgeBasePageQuery query);

    /** 查询知识库详情。 */
    KnowledgeBaseVO detail(Long id);

    /** 新增或修改知识库，id为空时新增否则修改。 */
    KnowledgeBaseVO save(Long id, SaveKnowledgeBaseRequest request, Long operatorId);

    /** 删除无文档的知识库。 */
    void delete(Long id, Long operatorId);
    /** 查询实时的应用端读取范围。 */
    KnowledgeAccessVO access(Long id);

    /** 在一个事务中替换读取范围和授权主体。 */
    KnowledgeAccessVO saveAccess(Long id, SaveKnowledgeAccessRequest request, Long operatorId);
}

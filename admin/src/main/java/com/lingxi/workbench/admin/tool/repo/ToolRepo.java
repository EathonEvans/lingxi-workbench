package com.lingxi.workbench.admin.tool.repo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.model.entity.ToolEntity;
import com.lingxi.workbench.admin.tool.model.request.ToolPageQuery;
import com.lingxi.workbench.admin.tool.repo.mapper.ToolMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Tool 数据仓库，封装 MyBatis-Plus 查询细节。 */
@Repository
@RequiredArgsConstructor
public class ToolRepo {

    private final ToolMapper mapper;

    /** 按关键字、来源和状态分页查询。 */
    public PageResult<ToolEntity> findPage(ToolPageQuery query) {
        IPage<ToolEntity> page = mapper.selectPage(query.toPage(), Wrappers.<ToolEntity>lambdaQuery()
                .and(CharSequenceUtil.isNotBlank(query.getKeyword()), wrapper -> wrapper
                        .like(ToolEntity::getName, query.getKeyword())
                        .or()
                        .like(ToolEntity::getCode, query.getKeyword()))
                .eq(Objects.nonNull(query.getSourceType()), ToolEntity::getSourceType, query.getSourceType())
                .eq(Objects.nonNull(query.getStatus()), ToolEntity::getStatus, query.getStatus())
                .orderByDesc(ToolEntity::getUpdatedAt)
                .orderByDesc(ToolEntity::getId));
        return PageResult.convert(page);
    }

    /** 按主键查询单条 Tool。 */
    public Optional<ToolEntity> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    /** 查询所有启用 Tool，供 Agent 配置下拉选择。 */
    public List<ToolEntity> findAllActive() {
        return mapper.selectList(Wrappers.<ToolEntity>lambdaQuery()
                .eq(ToolEntity::getStatus, EnableStatus.ACTIVE)
                .orderByAsc(ToolEntity::getName)
                .orderByAsc(ToolEntity::getId));
    }

    /** 批量查询给定编码中处于启用状态的编码，供 Agent 发布校验。 */
    public Set<String> findActiveCodes(List<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return Set.of();
        }
        return mapper.selectList(Wrappers.<ToolEntity>lambdaQuery()
                        .select(ToolEntity::getCode)
                        .in(ToolEntity::getCode, codes)
                        .eq(ToolEntity::getStatus, EnableStatus.ACTIVE))
                .stream()
                .map(ToolEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 判断稳定编码是否已被占用。 */
    public boolean existsByCode(String code) {
        return mapper.exists(Wrappers.<ToolEntity>lambdaQuery().eq(ToolEntity::getCode, code));
    }

    /** 根据主键是否存在选择插入或更新。 */
    public void save(ToolEntity entity) {
        if (Objects.isNull(entity.getId())) {
            mapper.insert(entity);
            return;
        }
        mapper.updateById(entity);
    }
}

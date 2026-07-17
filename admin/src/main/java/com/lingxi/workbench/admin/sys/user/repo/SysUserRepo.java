package com.lingxi.workbench.admin.sys.user.repo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.sys.user.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import com.lingxi.workbench.admin.sys.user.repo.mapper.SysUserMapper;
import com.lingxi.workbench.framework.convention.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SysUserRepo {

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;

    public Optional<SysUserBO> findByUsername(String normalizedUsername) {
        SysUserEntity entity = sysUserMapper.selectUserByUsernameWithAccess(normalizedUsername);
        return Optional.ofNullable(entity).map(sysUserConverter::toBO);
    }

    public Optional<SysUserBO> findById(Long id) {
        return Optional.ofNullable(sysUserMapper.selectUserByIdWithAccess(id))
                .map(sysUserConverter::toBO);
    }

    /** 按条件分页查询系统用户及其两端访问状态。 */
    public PageResult<SysUserBO> findPage(SysUserPageQuery query) {
        IPage<SysUserEntity> page = sysUserMapper.selectUserPage(
                query.toPage(), query);
        List<SysUserBO> records = page.getRecords().stream()
                .map(sysUserConverter::toBO)
                .toList();
        return new PageResult<>(records, Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()), page.getTotal());
    }

    public boolean existsByUsername(String username) {
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username));
    }

    public boolean existsByEmail(String email, Long excludeId) {
        if (Objects.isNull(email)) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getEmail, email)
                .ne(Objects.nonNull(excludeId), SysUserEntity::getId, excludeId));
    }

    public boolean existsByMobile(String mobile, Long excludeId) {
        if (Objects.isNull(mobile)) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getMobile, mobile)
                .ne(Objects.nonNull(excludeId), SysUserEntity::getId, excludeId));
    }

    public void saveApplicationAccess(Long userId, boolean enabled, Long operatorId) {
        sysUserMapper.upsertApplicationAccess(userId, enabled, operatorId);
    }

    public void insert(SysUserEntity entity) {
        sysUserMapper.insert(entity);
    }

    public void update(SysUserEntity entity) {
        sysUserMapper.updateById(entity);
    }
}

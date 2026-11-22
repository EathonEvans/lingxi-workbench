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

import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

/**
 * 系统用户数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class SysUserRepo {

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;

    /**
     * 按登录账号查询用户及两端访问状态。
     */
    public Optional<SysUserBO> findByUsername(String normalizedUsername) {
        SysUserEntity entity = sysUserMapper.selectUserByUsernameWithAccess(normalizedUsername);
        return Optional.ofNullable(entity).map(sysUserConverter::toBO);
    }

    /**
     * 按主键查询用户及两端访问状态。
     */
    public Optional<SysUserBO> findById(Long id) {
        return Optional.ofNullable(sysUserMapper.selectUserByIdWithAccess(id))
                .map(sysUserConverter::toBO);
    }

    /**
     * 按条件分页查询系统用户及其两端访问状态。
     */
    public PageResult<SysUserBO> findPage(SysUserPageQuery query) {
        IPage<SysUserEntity> page = sysUserMapper.selectUserPage(
                query.toPage(), query);
        List<SysUserBO> records = page.getRecords().stream()
                .map(sysUserConverter::toBO)
                .toList();
        return new PageResult<>(records, Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()), page.getTotal());
    }

    /**
     * 判断登录账号是否已存在。
     */
    public boolean existsByUsername(String username) {
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username));
    }

    /**
     * 判断邮箱是否已被其他用户占用，可通过 excludeId 排除当前用户。
     */
    public boolean existsByEmail(String email, Long excludeId) {
        if (Objects.isNull(email)) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getEmail, email)
                .ne(Objects.nonNull(excludeId), SysUserEntity::getId, excludeId));
    }

    /**
     * 判断手机号是否已被其他用户占用，可通过 excludeId 排除当前用户。
     */
    public boolean existsByMobile(String mobile, Long excludeId) {
        if (Objects.isNull(mobile)) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getMobile, mobile)
                .ne(Objects.nonNull(excludeId), SysUserEntity::getId, excludeId));
    }

    /**
     * 保存或更新用户应用端访问权限。
     */
    public void saveApplicationAccess(Long userId, boolean enabled, Long operatorId) {
        sysUserMapper.upsertApplicationAccess(userId, enabled, operatorId);
    }

    /**
     * 新增用户。
     */
    public void insert(SysUserEntity entity) {
        sysUserMapper.insert(entity);
    }

    /**
     * 更新用户。
     */
    public void update(SysUserEntity entity) {
        sysUserMapper.updateById(entity);
    }

    /** 仅更新用户头像字段，避免部分实体更新清空其他可空字段。 */
    public void updateAvatar(Long userId, String avatarObjectKey) {
        sysUserMapper.update(Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getAvatarUrl, avatarObjectKey));
    }
    public boolean existsAll(Set<Long> ids) {
        return ids.isEmpty() || sysUserMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .in(SysUserEntity::getId, ids)) == ids.size();
    }
}

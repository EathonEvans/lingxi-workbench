package com.lingxi.workbench.admin.auth.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.auth.model.bo.SysUserBO;
import com.lingxi.workbench.admin.auth.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.auth.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.auth.repo.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SysUserRepo {

    private final SysUserMapper sysUserMapper;
    private final SysUserConverter sysUserConverter;

    public Optional<SysUserBO> findByUsername(String normalizedUsername) {
        SysUserEntity entity = sysUserMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery().eq(SysUserEntity::getUsername, normalizedUsername));
        return Optional.ofNullable(entity).map(sysUserConverter::toBO);
    }

    public Optional<SysUserBO> findById(Long id) {
        return Optional.ofNullable(sysUserMapper.selectById(id)).map(sysUserConverter::toBO);
    }

    /** 按创建顺序查询全部系统用户。 */
    public List<SysUserBO> findAll() {
        return sysUserMapper.selectUserList()
                .stream()
                .map(sysUserConverter::toBO)
                .toList();
    }

    public boolean existsByUsername(String username) {
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username));
    }

    public boolean existsByEmail(String email, Long excludeId) {
        if (email == null) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getEmail, email)
                .ne(excludeId != null, SysUserEntity::getId, excludeId));
    }

    public boolean existsByMobile(String mobile, Long excludeId) {
        if (mobile == null) return false;
        return sysUserMapper.exists(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getMobile, mobile)
                .ne(excludeId != null, SysUserEntity::getId, excludeId));
    }

    public void saveApplicationAccess(Long userId, boolean enabled, Long operatorId) {
        sysUserMapper.upsertApplicationAccess(userId, enabled, operatorId);
    }

    public void recordChange(Long targetUserId, Long operatorUserId, String action, String summary) {
        sysUserMapper.insertChangeLog(targetUserId, operatorUserId, action, summary);
    }

    public void insert(SysUserEntity entity) {
        sysUserMapper.insert(entity);
    }

    public void update(SysUserEntity entity) {
        sysUserMapper.updateById(entity);
    }
}

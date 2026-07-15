package com.lingxi.workbench.admin.auth.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.converter.AdminAccountConverter;
import com.lingxi.workbench.admin.auth.model.entity.AdminAccountEntity;
import com.lingxi.workbench.admin.auth.repo.mapper.AdminAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminAccountRepo {

    private final AdminAccountMapper adminAccountMapper;
    private final AdminAccountConverter adminAccountConverter;

    public Optional<AdminAccountBO> findByUsername(String normalizedUsername) {
        AdminAccountEntity entity = adminAccountMapper.selectOne(Wrappers.<AdminAccountEntity>lambdaQuery().eq(AdminAccountEntity::getUsername, normalizedUsername));
        return Optional.ofNullable(entity).map(adminAccountConverter::toBO);
    }

    public Optional<AdminAccountBO> findById(Long id) {
        return Optional.ofNullable(adminAccountMapper.selectById(id)).map(adminAccountConverter::toBO);
    }

    /** 按创建顺序查询全部后台管理员账号。 */
    public List<AdminAccountBO> findAll() {
        return adminAccountMapper.selectList(Wrappers.<AdminAccountEntity>lambdaQuery()
                        .orderByAsc(AdminAccountEntity::getId))
                .stream()
                .map(adminAccountConverter::toBO)
                .toList();
    }

    public boolean existsByUsername(String username) {
        return adminAccountMapper.exists(Wrappers.<AdminAccountEntity>lambdaQuery()
                .eq(AdminAccountEntity::getUsername, username));
    }

    public void insert(AdminAccountEntity entity) {
        adminAccountMapper.insert(entity);
    }

    public void update(AdminAccountEntity entity) {
        adminAccountMapper.updateById(entity);
    }
}

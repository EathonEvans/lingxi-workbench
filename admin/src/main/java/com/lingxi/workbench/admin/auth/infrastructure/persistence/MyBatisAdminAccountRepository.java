package com.lingxi.workbench.admin.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import com.lingxi.workbench.admin.auth.domain.AdminAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public final class MyBatisAdminAccountRepository implements AdminAccountRepository {

    private final AdminAccountMapper adminAccountMapper;
    private final AdminAccountConverter adminAccountConverter;

    public MyBatisAdminAccountRepository(
            AdminAccountMapper adminAccountMapper,
            AdminAccountConverter adminAccountConverter
    ) {
        this.adminAccountMapper = adminAccountMapper;
        this.adminAccountConverter = adminAccountConverter;
    }

    @Override
    public Optional<AdminAccount> findByUsername(String normalizedUsername) {
        AdminAccountEntity entity = adminAccountMapper.selectOne(
                Wrappers.<AdminAccountEntity>lambdaQuery()
                        .eq(AdminAccountEntity::getUsername, normalizedUsername)
        );
        return Optional.ofNullable(entity).map(adminAccountConverter::toDomain);
    }

    @Override
    public Optional<AdminAccount> findById(Long id) {
        return Optional.ofNullable(adminAccountMapper.selectById(id))
                .map(adminAccountConverter::toDomain);
    }
}

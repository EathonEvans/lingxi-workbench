package com.lingxi.workbench.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.auth.domain.UserAccount;
import com.lingxi.workbench.auth.domain.UserAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public final class MyBatisUserAccountRepository implements UserAccountRepository {

    private final UserAccountMapper userAccountMapper;
    private final UserAccountConverter userAccountConverter;

    public MyBatisUserAccountRepository(
            UserAccountMapper userAccountMapper,
            UserAccountConverter userAccountConverter
    ) {
        this.userAccountMapper = userAccountMapper;
        this.userAccountConverter = userAccountConverter;
    }

    @Override
    public Optional<UserAccount> findByUsername(String normalizedUsername) {
        UserAccountEntity entity = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getUsername, normalizedUsername)
        );
        return Optional.ofNullable(entity).map(userAccountConverter::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(userAccountMapper.selectById(id))
                .map(userAccountConverter::toDomain);
    }
}

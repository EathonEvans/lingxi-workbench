package com.lingxi.workbench.auth.repo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.model.converter.UserAccountConverter;
import com.lingxi.workbench.auth.model.entity.UserAccountEntity;
import com.lingxi.workbench.auth.repo.mapper.UserAccountMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserAccountRepo {

    private final UserAccountMapper userAccountMapper;
    private final UserAccountConverter userAccountConverter;

    public UserAccountRepo(
            UserAccountMapper userAccountMapper,
            UserAccountConverter userAccountConverter
    ) {
        this.userAccountMapper = userAccountMapper;
        this.userAccountConverter = userAccountConverter;
    }

    public Optional<UserAccountBO> findByUsername(String normalizedUsername) {
        UserAccountEntity entity = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getUsername, normalizedUsername)
        );
        return Optional.ofNullable(entity).map(userAccountConverter::toBO);
    }

    public Optional<UserAccountBO> findById(Long id) {
        return Optional.ofNullable(userAccountMapper.selectById(id))
                .map(userAccountConverter::toBO);
    }
}

package com.lingxi.workbench.auth.repo;

import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepo {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    public UserRepo(
            UserMapper userMapper,
            UserConverter userConverter
    ) {
        this.userMapper = userMapper;
        this.userConverter = userConverter;
    }

    public Optional<UserBO> findByUsername(String normalizedUsername) {
        UserEntity entity = userMapper.selectByUsernameWithAccess(normalizedUsername);
        return Optional.ofNullable(entity).map(userConverter::toBO);
    }

    public Optional<UserBO> findById(Long id) {
        return Optional.ofNullable(userMapper.selectByIdWithAccess(id))
                .map(userConverter::toBO);
    }
}

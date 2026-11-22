package com.lingxi.workbench.auth.repo;

import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层，封装 Mapper 调用并完成 Entity 到 BO 的转换。
 */
@Repository
@RequiredArgsConstructor
public class UserRepo {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    /**
     * 根据用户名（已标准化处理）查询用户，联表获取应用端访问权限。
     *
     * @param normalizedUsername 标准化后的用户名
     * @return 用户业务对象，不存在时返回空 Optional
     */
    public Optional<UserBO> findByUsername(String normalizedUsername) {
        UserEntity entity = userMapper.selectByUsernameWithAccess(normalizedUsername);
        return Optional.ofNullable(entity).map(userConverter::toBO);
    }

    /**
     * 根据用户 ID 查询用户，联表获取应用端访问权限。
     *
     * @param id 用户主键
     * @return 用户业务对象，不存在时返回空 Optional
     */
    public Optional<UserBO> findById(Long id) {
        return Optional.ofNullable(userMapper.selectByIdWithAccess(id))
                .map(userConverter::toBO);
    }
}

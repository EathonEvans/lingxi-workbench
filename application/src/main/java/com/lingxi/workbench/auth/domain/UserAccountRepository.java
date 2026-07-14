package com.lingxi.workbench.auth.domain;

import java.util.Optional;

public interface UserAccountRepository {

    Optional<UserAccount> findByUsername(String normalizedUsername);

    Optional<UserAccount> findById(Long id);
}

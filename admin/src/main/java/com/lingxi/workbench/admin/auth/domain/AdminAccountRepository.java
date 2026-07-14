package com.lingxi.workbench.admin.auth.domain;

import java.util.Optional;

public interface AdminAccountRepository {

    Optional<AdminAccount> findByUsername(String normalizedUsername);

    Optional<AdminAccount> findById(Long id);
}

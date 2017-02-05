package com.coinblesk.server.dao;

import com.coinblesk.server.entity.UserAccount;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends CrudRepository<UserAccount, Long>
{
    Optional<UserAccount> findOptionalByEmail(String email);

    UserAccount findByEmail(String email);
}

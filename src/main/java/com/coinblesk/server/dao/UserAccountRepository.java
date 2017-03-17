package com.coinblesk.server.dao;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.UserAccount;

public interface UserAccountRepository extends CrudRepository<UserAccount, Long> {

	Optional<UserAccount> findOptionalByEmail(String email);

	UserAccount findByEmail(String email);
}

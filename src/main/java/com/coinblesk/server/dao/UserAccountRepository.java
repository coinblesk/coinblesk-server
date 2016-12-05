package com.coinblesk.server.dao;

import com.coinblesk.server.entity.UserAccount;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface UserAccountRepository extends CrudRepository<UserAccount, Long>
{
    UserAccount findByEmail(String email);
}

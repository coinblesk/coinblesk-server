/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.dao.UserAccountDAO;
import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author draft
 */

@Service
public class UserAccountService {
    @Autowired
    private UserAccountDAO userAccountDao;
    
    @Transactional
    public UserAccount getByUsername(String username) {
        return userAccountDao.getByAttribute("username", username);
    }
    
    @Transactional
    public void save(UserAccount userAccount) {
        userAccountDao.save(userAccount);
    }
}

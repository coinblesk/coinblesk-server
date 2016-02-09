/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.config.AdminEmail;
import ch.uzh.csg.coinblesk.server.dao.UserAccountDAO;
import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import ch.uzh.csg.coinblesk.server.utils.Pair;
import com.coinblesk.json.StatusTO;
import com.coinblesk.json.UserAccountTO;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 *
 * @author draft
 */

@Service
public class UserAccountService {

    //as senn in: https://www.w3.org/TR/html5/forms.html#valid-e-mail-address
    public static final String EMAIL_REGEX = "/^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/";
    
    @Autowired
    private UserAccountDAO userAccountDao;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Transactional
    public UserAccount getByUsername(String username) {
        return userAccountDao.getByAttribute("username", username);
    }
    
    private UserAccount getByEmail(String email) {
        return userAccountDao.getByAttribute("email", email);
    }
    
    @Transactional
    public void save(UserAccount userAccount) {
        userAccountDao.save(userAccount);
    }
    
    @Transactional
    public Pair<StatusTO, UserAccount> create(final UserAccountTO userAccountTO) {
        final String email = userAccountTO.email();
        if(email == null){
            return new Pair(new StatusTO().reason(StatusTO.Reason.NO_EMAIL), null);
	}
        if(!email.matches(EMAIL_REGEX)){
            return new Pair(new StatusTO().reason(StatusTO.Reason.INVALID_EMAIL), null);
	}
        if(userAccountTO.password() == null || userAccountTO.password().length() < 6) {
            return new Pair(new StatusTO().reason(StatusTO.Reason.PASSWORD_TOO_SHORT), null);
	}
        
        final UserAccount found = userAccountDao.getByAttribute("email", email);
        if(found != null) {
            if(found.getEmailToken()!=null) {
                return new Pair(new StatusTO().reason(StatusTO.Reason.EMAIL_ALREADY_EXISTS_NOT_ACTIVATED), found);
            }
            return new Pair(new StatusTO().reason(StatusTO.Reason.EMAIL_ALREADY_EXISTS_ACTIVATED), found);
        }
        
        //convert TO to Entity
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(email);
        userAccount.setPassword(passwordEncoder.encode(userAccountTO.password()));
        userAccount.setCreationDate(new Date());
	userAccount.setDeleted(false);
	userAccount.setVersion((byte)2);
        userAccount.setEmailToken(UUID.randomUUID().toString());
        userAccountDao.save(userAccount);
        return new Pair(new StatusTO().setSuccess(), userAccount);
    }

    @Transactional
    public StatusTO activate(String email, String token) {
        final UserAccount found = userAccountDao.getByAttribute("email", email);
        if(found == null) {
            //no such email address - notok
            return new StatusTO().reason(StatusTO.Reason.NO_EMAIL);
        }
        if(found.getEmailToken() == null) {
            //already acitaveted - ok
            return new StatusTO().setSuccess();
        }
        
        if(!found.getEmailToken().equals(token)) {
            //wrong token - notok
            return new StatusTO().reason(StatusTO.Reason.INVALID_EMAIL_TOKEN);
        }
        
        //activate, enitiy is in attached state
        found.setEmailToken(null);
        return new StatusTO().setSuccess();
    }
}

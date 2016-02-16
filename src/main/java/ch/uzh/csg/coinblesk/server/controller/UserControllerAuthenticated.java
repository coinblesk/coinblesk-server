/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AdminEmail;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import com.coinblesk.json.UserAccountStatusTO;
import com.coinblesk.json.UserAccountTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author draft
 */
@RestController
@RequestMapping(value = {"/user/a", "user/auth", "u/auth", "/u/a"})
public class UserControllerAuthenticated {
    
    private final static Logger LOG = LoggerFactory.getLogger(UserControllerAuthenticated.class);

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private AdminEmail adminEmail;
    
    @RequestMapping(value = {"/delete", "/d"}, method = RequestMethod.PATCH,
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public UserAccountStatusTO deleteAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LOG.debug("Delete account for {}", auth.getName());
        try {
            UserAccountStatusTO status = userAccountService.delete(auth.getName());
            if(!status.isSuccess()) {
                LOG.error("Someone tried a delete account with an invalid username: {}/{}", auth, status.reason().name());
                adminEmail.send("Wrong Delete Account?", "Someone tried a delete account with an invalid username: " + auth + "/" + status.reason().name());
            }
            LOG.debug("Delete account success for {}", auth.getName());
            return status;
        } catch (Exception e) {
            LOG.error("User create error", e);
            return new UserAccountStatusTO().reason(UserAccountStatusTO.Reason.SERVER_ERROR).message(e.getMessage());
        }
    }
    
    @RequestMapping(value = {"/get", "/g"}, method = RequestMethod.GET,
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public UserAccountTO getAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LOG.debug("Get account for {}", auth.getName());
        try {
            UserAccountTO userAccount = userAccountService.get(auth.getName());
            if(userAccount == null) {
                LOG.error("Someone tried a delete account with an invalid username: {}", auth);
                adminEmail.send("Wrong Delete Account?", "Someone tried a delete account with an invalid username: " + auth);
                return null;
            }
            LOG.debug("Get account success for {}", auth.getName());
            return userAccount;
        } catch (Exception e) {
            LOG.error("User create error", e);
            return null;
        }
    }
}

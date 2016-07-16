/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.controller;

import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.server.config.AdminEmail;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.DatabaseConfig;
import com.coinblesk.server.config.UserEmail;
import com.coinblesk.util.Pair;
import java.net.URLEncoder;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = {"/user", "/u"})
@ApiVersion({"v1", ""})
public class UserController {

    private final static Logger LOG = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private AdminEmail adminEmail;
    
    @Autowired
    private UserEmail userEmail;
    
    @Autowired
    private MessageSource messageSource;
    
    @Autowired
    private AppConfig cfg;
    
    @Autowired DatabaseConfig databaseConfig;

    //CRUD for the user
    @RequestMapping(value = {"/create", "/c"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public UserAccountStatusTO createAccount(Locale locale, 
            @RequestBody UserAccountTO userAccount) {
        LOG.debug("Create account for {}", userAccount.email());
        try {
            //TODO: reactived if deleted flag is set
            Pair<UserAccountStatusTO, UserAccount> pair = userAccountService.create(userAccount);
            if ((pair.element0().isSuccess()
                    || pair.element0().type() == Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED)
                    && pair.element1() != null && pair.element1().getEmailToken() != null) {
                
                try {
                    LOG.debug("send email to {}", pair.element1().getEmail());
                    final String path = "v1/user/verify/"+URLEncoder.encode(pair.element1().getEmail(), "UTF-8")+"/"+pair.element1().getEmailToken();
                    final String url;
                    if(cfg.getBitcoinNet() == BitcoinNet.MAINNET) {
                        url = "https://bitcoin.csg.uzh.ch/coinblesk-server/"+path;
                    } else {
                        url = "http://bitcoin2-test.csg.uzh.ch/coinblesk-server/"+path;
                    }
                    userEmail.send(pair.element1().getEmail(), 
                            messageSource.getMessage("activation.email.title", null, locale), 
                            messageSource.getMessage("activation.email.text", new String[]{url}, locale));
                } catch (Exception e) {
                    LOG.error("Mail send error", e);
                    adminEmail.send("Coinblesk Error", "Unexpected Error: " + e);
                }
            }
            return pair.element0();
        } catch (Exception e) {
            LOG.error("User create error", e);
            return new UserAccountStatusTO().type(Type.SERVER_ERROR).message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/verify/{email}/{token}", "/v/{email}/{token}"}, method = RequestMethod.GET)
    @ResponseBody
    public String verifyEmail(@PathVariable(value = "email") String email,
            @PathVariable(value = "token") String token, HttpServletRequest request) {
        LOG.debug("Activate account for {}", email);
        try {
            UserAccountStatusTO status = userAccountService.activate(email, token);
            if (!status.isSuccess()) {
                LOG.error("Someone tried a link with an invalid token: {}/{}/{}", email, token, status.type()
                        .name());
                adminEmail.send("Wrong Link?",
                        "Someone tried a link with an invalid token: " + email + " / " + token + "/" + status
                        .type().name());
                throw new BadRequestException("Wrong Link");
            }
            LOG.debug("Activate account success for {}", email);
            //TODO: text/layout
            return "Activate account success";
        } catch (Exception e) {
            LOG.error("User create error", e);
            throw new InternalServerErrorException(e);
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public class BadRequestException extends RuntimeException {

        public BadRequestException(String reason) {
            super(reason);
        }
    }

    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public class InternalServerErrorException extends RuntimeException {

        public InternalServerErrorException(Throwable t) {
            super(t);
        }
    }
}

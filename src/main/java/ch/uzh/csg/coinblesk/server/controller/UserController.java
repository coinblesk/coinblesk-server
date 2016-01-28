/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import com.coinblesk.responseobject.ExchangeRateTransferObject;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author draft
 */
@RestController
@RequestMapping(value={"/v1/user", "/v1/u"})
public class UserController {
    @RequestMapping(value = "/test", method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public String getInfo() {
        return "yes";
    }
}

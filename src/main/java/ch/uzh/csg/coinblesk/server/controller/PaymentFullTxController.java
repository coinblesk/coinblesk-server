/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.VerifyTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated This class is here for compatibility reasons
 * 
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 *
 */
@RestController
@RequestMapping(value = {"/full-payment", "/f"})
@ApiVersion({"v1", ""})
public class PaymentFullTxController {

    @Autowired
    private PaymentController paymentController;

    @RequestMapping(value = {"/sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public SignTO sign(@RequestBody SignTO input) {
        return paymentController.sign(input);
    }
    
     @RequestMapping(value = {"/verify", "/v"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public VerifyTO verify(@RequestBody VerifyTO input) {
        return paymentController.verify(input);
    }
}

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.json.v1.SignTO;
import com.coinblesk.json.v1.VerifyTO;
import com.coinblesk.server.utils.ApiVersion;

/**
 * @deprecated This class is here for compatibility reasons
 *
 * @author Alessandro De Carli
 * @author Thomas Bocek
 *
 */
@Deprecated
@RestController
@RequestMapping(value = "/full-payment")
@ApiVersion({"v1", ""})
public class PaymentFullTxController {

    @Autowired
    private PaymentController paymentController;

    @RequestMapping(value = "/sign", method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public SignTO sign(@RequestBody SignTO input) {
        return paymentController.sign(input);
    }

    @RequestMapping(value = "/verify", method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public VerifyTO verify(@RequestBody VerifyTO input) {
        return paymentController.verify(input);
    }
}

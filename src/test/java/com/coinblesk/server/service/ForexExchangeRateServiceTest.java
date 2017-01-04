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
package com.coinblesk.server.service;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.Map;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ForexExchangeRateServiceTest {

    @Autowired
    private ForexService forexExchangeRateService;
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("coinblesk.config.dir", "/tmp/lib/coinblesk");
    }

    @Test
    public void testForex() throws Exception {
        BigDecimal d = forexExchangeRateService.getExchangeRate("USD", "CHF");
        Assert.assertNotNull(d);
        System.out.println("rate is: " + d);
    }

    @Test
    public void testForexMulti1() throws Exception {
        Map<String, BigDecimal> m = forexExchangeRateService.getExchangeRates("CHFUSD", "USDEUR");
        Assert.assertNotNull(m);
        System.out.println("rate is: " + m);
    }

    @Test
    public void testForexMulti2() throws Exception {
        //TODO: check if cached
        Map<String, BigDecimal> m = forexExchangeRateService.getExchangeRates("CHFUSD", "USDEUR");
        Assert.assertNotNull(m);
        System.out.println("rate is: " + m);
    }

}

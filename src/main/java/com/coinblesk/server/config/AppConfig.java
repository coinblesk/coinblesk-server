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
package com.coinblesk.server.config;

import com.coinblesk.server.utils.CoinUtils;
import com.coinblesk.bitcoin.BitcoinNet;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import org.bitcoinj.core.NetworkParameters;

/**
 * This is the default configuration for testcases. If you want to change these settings e.g. when using
 * tomcat, add these values to context.xml:
 *
 * <pre>
 *  ...
 *  <Parameter name="bitcoin.net" value="testnet" />
 *  ...
 * </pre>
 *
 * @author Raphael Voellmy
 * @author Thomas Bocek
 */
@Configuration
public class AppConfig {

    @Value("${coinblesk.config.dir:/var/lib/coinblesk}")
    private FileSystemResource configDir;

    @Value("${bitcoin.net:unittest}")
    private String bitcoinNet;

    @Value("${bitcoin.minconf:1}")
    private int minConf;


    public FileSystemResource getConfigDir() {
        if (configDir != null && !configDir.exists()) {
            if(!configDir.getFile().mkdirs()) {
                throw new RuntimeException("The directory " + configDir + " does not exist");
            }
        }
        return configDir;
    }

    public String getBitcoinNet() {
        return bitcoinNet;
    }

    public NetworkParameters getNetworkParameters() {
        return CoinUtils.getNetworkParams(BitcoinNet.of(bitcoinNet));
    }

    public int getMinConf() {
        return minConf;
    }
}

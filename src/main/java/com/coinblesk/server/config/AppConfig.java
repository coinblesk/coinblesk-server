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

import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.bitcoinj.core.NetworkParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.server.utils.CoinUtils;
import java.math.BigInteger;
import org.bitcoinj.core.ECKey;
import org.slf4j.LoggerFactory;

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
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AppConfig.class);
    
    private final static Set<String> SUPPORTED_CLIENT_VERSIONS;
	static {
		SUPPORTED_CLIENT_VERSIONS = new HashSet<>();
		SUPPORTED_CLIENT_VERSIONS.add("1.0"); // CeBIT release (v1.0.262, should be v2.1)
		SUPPORTED_CLIENT_VERSIONS.add("2.2"); // CLTV release
	}

    @Value("${coinblesk.config.dir:/var/lib/coinblesk}")
    private FileSystemResource configDir;

    @Value("${bitcoin.net:unittest}")
    private String bitcoinNet;

    @Value("${bitcoin.minconf:1}")
    private int minConf;
    
    //this private key is just use for unit tests
    @Value("${bitcoin.potprivkey:97324063353421115888582782536755703931560774174498831848725083330146537953701}")
    private BigInteger potPrivateKeyAddress;

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    
    public FileSystemResource getConfigDir() {
        if (configDir != null && !configDir.exists()) {
            if(!configDir.getFile().mkdirs()) {
                throw new RuntimeException("The directory " + configDir + " does not exist");
            }
        }
        return configDir;
    }

    public BitcoinNet getBitcoinNet() {
        return BitcoinNet.of(bitcoinNet);
    }

    public NetworkParameters getNetworkParameters() {
        return CoinUtils.getNetworkParams(getBitcoinNet());
    }

    public int getMinConf() {
        return minConf;
    }
    
    public Set<String> getSupportedClientVersions() {
    	return SUPPORTED_CLIENT_VERSIONS;
    }
    
    public ECKey getPotPrivateKeyAddress() {
        if(potPrivateKeyAddress == null || potPrivateKeyAddress.equals("")) {
            ECKey ecKey = new ECKey();
            LOG.error("No private key defined, cannot continue, suggested key:{}", ecKey.getPrivKey());
            throw new RuntimeException("No private key defined, cannot continue, suggested key:" + ecKey.getPrivKey());
        }
        else if(potPrivateKeyAddress.equals("0")) {
            LOG.warn("Using unit-test public key");
            return ECKey.fromPrivate(potPrivateKeyAddress);
        }
        
        return ECKey.fromPrivate(potPrivateKeyAddress);
    }
}

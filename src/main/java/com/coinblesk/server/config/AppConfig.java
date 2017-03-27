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

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.server.utils.CoinUtils;
import org.springframework.retry.annotation.EnableRetry;

/**
 * This is the default configuration for testcases. If you want to change these
 * settings e.g. when using tomcat, add these values to context.xml:
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
@EnableRetry
public class AppConfig {

	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AppConfig.class);

	private final static Set<String> SUPPORTED_CLIENT_VERSIONS;
	static {
		SUPPORTED_CLIENT_VERSIONS = new HashSet<>();
		// SUPPORTED_CLIENT_VERSIONS.add("1.0"); // CeBIT release (v1.0.262,
		// should be v2.1)
		// SUPPORTED_CLIENT_VERSIONS.add("2.2"); // CLTV release
		SUPPORTED_CLIENT_VERSIONS.add("2.3"); // TO versioning release
	}

	@Value("${coinblesk.url}")
	private String url;

	@Value("${coinblesk.config.dir}")
	private FileSystemResource configDir;

	@Value("${bitcoin.net}")
	private String bitcoinNet;

	@Value("${bitcoin.firstSeedNode}")
	private String firstSeedNode;

	@Value("${bitcoin.minconf}")
	private int minConf;

	@Value("${security.jwt.secret}")
	private String jwtSecret;

	@Value("${security.jwt.validityInSeconds}")
	private Long jwtValidityInSeconds;

	@Value("${security.jwt.adminValidityInSeconds}")
	private Long jwtAdminValidityInSeconds;

	// this private key is just use for unit tests
	@Value("${bitcoin.potprivkey}")
	private BigInteger potPrivateKeyAddress;

	@Value("${bitcoin.potCreationTime}")
	private Long potCreationTime;

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public FileSystemResource getConfigDir() {
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
		return new HashSet<>(SUPPORTED_CLIENT_VERSIONS);
	}

	public ECKey getPotPrivateKeyAddress() {

		if (potPrivateKeyAddress.equals(new BigInteger(""))) {
			ECKey ecKey = new ECKey();
			LOG.error("No private key defined, cannot continue, suggested key:{}", ecKey.getPrivKey());
			throw new RuntimeException("No private key defined, cannot continue, suggested key:" + ecKey.getPrivKey());
		}

		ECKey ecKey = ECKey.fromPrivate(potPrivateKeyAddress);
		LOG.info("Pot address is: {}", ecKey.toAddress(getNetworkParameters()));
		return ecKey;
	}

	public String getUrl() {
		return url;
	}

	public String getJwtSecret() {
		return jwtSecret;
	}

	public Long getJwtValidityInSeconds() {
		return jwtValidityInSeconds;
	}

	public Long getJwtAdminValidityInSeconds() {
		return jwtAdminValidityInSeconds;
	}

	public Long getPotCreationTime() {
		return potCreationTime;
	}

	public String getFirstSeedNode() {
		return firstSeedNode;
	}
}

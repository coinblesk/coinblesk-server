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
package com.coinblesk.server.utils;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;

import com.coinblesk.bitcoin.BitcoinNet;

/**
 *
 * @author Thomas Bocek
 */
public class CoinUtils {
	/**
	 *
	 * @param bitcoinNet
	 * @return the {@link NetworkParameters} for a specific network
	 */
	public static NetworkParameters getNetworkParams(BitcoinNet bitcoinNet) {
		switch (bitcoinNet) {
		case UNITTEST:
			return UnitTestParams.get();
		case REGTEST:
			return RegTestParams.get();
		case TESTNET:
			return TestNet3Params.get();
		case MAINNET:
			return MainNetParams.get();
		default:
			throw new RuntimeException("Please set the server property bitcoin.net to (unittest|regtest|testnet|main)");
		}
	}
}

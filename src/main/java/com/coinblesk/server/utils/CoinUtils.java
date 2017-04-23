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

import com.coinblesk.bitcoin.BitcoinNet;
import com.google.common.primitives.Longs;
import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Thomas Bocek
 */
public class CoinUtils {
	/**
	 * @param bitcoinNet
	 * @return the {@link NetworkParameters} for a specific network
	 */
	public static NetworkParameters getNetworkParams(BitcoinNet bitcoinNet) {
		switch (bitcoinNet) {
			case UNITTEST:
				return CoinbleskTestParams.get();
			case REGTEST:
				return RegTestParams.get();
			case TESTNET:
				return TestNet3Params.get();
			case MAINNET:
				return MainNetParams.get();
			default:
				throw new RuntimeException("Please set the server property bitcoin.net to " +
					"(unittest|regtest|testnet|main)");
		}
	}

	// https://bitcoin.stackexchange.com/questions/22059/how-can-i-track-transactions-in-a-way-that-is-not-affected-by-their-malleability
	public static Sha256Hash trackingHash(Transaction tx) {
		ByteArrayOutputStream out = new ByteArrayOutputStream( );
		try {
			out.write(Longs.toByteArray(tx.getVersion()));
			for (TransactionInput input : tx.getInputs()) {
				out.write(input.getOutpoint().getHash().getBytes());
				out.write(Longs.toByteArray(input.getOutpoint().getIndex()));
				out.write(Longs.toByteArray(input.getSequenceNumber()));
			}
			for (TransactionOutput output : tx.getOutputs()) {
				out.write(Longs.toByteArray(output.getValue().longValue()));
				out.write(output.getScriptBytes());
			}
			out.write(Longs.toByteArray(tx.getLockTime()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Sha256Hash.twiceOf(out.toByteArray());
	}
}

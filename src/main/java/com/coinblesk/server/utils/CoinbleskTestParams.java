package com.coinblesk.server.utils;

import org.bitcoinj.core.*;
import org.bitcoinj.params.AbstractBitcoinNetParams;

import java.math.BigInteger;

/**
 * Network parameters used by the bitcoinj unit tests (and potentially your own). This lets you solve a block using
 * {@link org.bitcoinj.core.Block#solve()} by setting difficulty to the easiest possible.
 */
public class CoinbleskTestParams extends AbstractBitcoinNetParams {
	public static final int UNITNET_MAJORITY_WINDOW = 8;
	public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 6;
	public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 4;

	public CoinbleskTestParams() {
		super();
		id = ID_UNITTESTNET;
		packetMagic = 0x0b110907;
		addressHeader = 111;
		p2shHeader = 196;
		acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
		maxTarget = new BigInteger("00ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
		genesisBlock.setTime(System.currentTimeMillis() / 1000);
		genesisBlock.setDifficultyTarget(Block.EASIEST_DIFFICULTY_TARGET);
		genesisBlock.solve();
		port = 18333;
		interval = 500000; // changed to something bigger to avoid difficulty transition errors
		dumpedPrivateKeyHeader = 239;
		targetTimespan = 200000000;  // 6 years. Just a very big number.
		spendableCoinbaseDepth = 5;
		subsidyDecreaseBlockCount = 100;
		dnsSeeds = null;
		addrSeeds = null;
		bip32HeaderPub = 0x043587CF;
		bip32HeaderPriv = 0x04358394;

		majorityEnforceBlockUpgrade = 3;
		majorityRejectBlockOutdated = 4;
		majorityWindow = 7;
	}

	private static CoinbleskTestParams instance;
	public static synchronized CoinbleskTestParams get() {
		if (instance == null) {
			instance = new CoinbleskTestParams();
		}
		return instance;
	}

	@Override
	public String getPaymentProtocolId() {
		return "unittest";
	}
}


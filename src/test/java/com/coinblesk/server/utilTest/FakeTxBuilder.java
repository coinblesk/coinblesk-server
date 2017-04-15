package com.coinblesk.server.utilTest;

/*
 * Copyright 2011 Google Inc.
 * Copyright 2016 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.commons.lang3.RandomUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.bitcoinj.core.Coin.valueOf;

public class FakeTxBuilder {
	/**
	 * Create a fake transaction, without change.
	 */
	public static Transaction createFakeTx(final NetworkParameters params) {
		return createFakeTxWithoutChangeAddress(params, Coin.COIN, new ECKey().toAddress(params));
	}

	public static Transaction createFakeP2SHTx(final NetworkParameters params) {
		// Transaction that sends to some (non-existing) script hash
		return createFakeTxWithoutChangeAddress(params, Coin.COIN, Address.fromP2SHHash(params, RandomUtils.nextBytes(20)));
	}

	/**
	 * Create a fake TX of sufficient realism to exercise the unit tests. Two
	 * outputs, one to us, one to somewhere else to simulate change. There is
	 * one random input.
	 */
	public static Transaction createFakeTxWithChangeAddress(NetworkParameters params, Coin value, Address to,
															Address changeOutput) {
		Transaction t = new Transaction(params);
		TransactionOutput outputToMe = new TransactionOutput(params, t, value, to);
		t.addOutput(outputToMe);
		TransactionOutput change = new TransactionOutput(params, t, valueOf(1, 11), changeOutput);
		t.addOutput(change);
		// Make a previous tx simply to send us sufficient coins. This prev tx
		// is not really valid but it doesn't
		// matter for our purposes.
		Transaction prevTx = new Transaction(params);
		TransactionOutput prevOut = new TransactionOutput(params, prevTx, value, to);
		prevTx.addOutput(prevOut);
		// Connect it.
		t.addInput(prevOut).setScriptSig(ScriptBuilder.createInputScript(TransactionSignature.dummy()));
		// Fake signature.
		// Serialize/deserialize to ensure internal state is stripped, as if it
		// had been read from the wire.
		return roundTripTransaction(params, t);
	}

	/**
	 * Create a fake TX for unit tests, for use with unit tests that need
	 * greater control. One outputs, 2 random inputs, split randomly to create
	 * randomness.
	 */
	public static Transaction createFakeTxWithoutChangeAddress(NetworkParameters params, Coin value, Address to) {
		Transaction t = new Transaction(params);
		TransactionOutput outputToMe = new TransactionOutput(params, t, value, to);
		t.addOutput(outputToMe);

		// Make a random split in the output value so we get a distinct hash
		// when we call this multiple times with same args
		long split = new Random().nextLong();
		if (split < 0) {
			split *= -1;
		}
		if (split == 0) {
			split = 15;
		}
		while (split > value.getValue()) {
			split /= 2;
		}

		// Make a previous tx simply to send us sufficient coins. This prev tx
		// is not really valid but it doesn't
		// matter for our purposes.
		Transaction prevTx1 = new Transaction(params);
		TransactionOutput prevOut1 = new TransactionOutput(params, prevTx1, Coin.valueOf(split), to);
		prevTx1.addOutput(prevOut1);
		// Connect it.
		t.addInput(prevOut1).setScriptSig(ScriptBuilder.createInputScript(TransactionSignature.dummy()));
		// Fake signature.

		// Do it again
		Transaction prevTx2 = new Transaction(params);
		TransactionOutput prevOut2 = new TransactionOutput(params, prevTx2, Coin.valueOf(value.getValue() - split), to);
		prevTx2.addOutput(prevOut2);
		t.addInput(prevOut2).setScriptSig(ScriptBuilder.createInputScript(TransactionSignature.dummy()));

		// Serialize/deserialize to ensure internal state is stripped, as if it
		// had been read from the wire.
		return roundTripTransaction(params, t);
	}

	/**
	 * Roundtrip a transaction so that it appears as if it has just come from
	 * the wire
	 */
	private static Transaction roundTripTransaction(NetworkParameters params, Transaction tx) {
		try {
			MessageSerializer bs = params.getDefaultSerializer();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bs.serialize(tx, bos);
			return (Transaction) bs.deserialize(ByteBuffer.wrap(bos.toByteArray()));
		} catch (IOException e) {
			throw new RuntimeException(e); // Should not happen.
		}
	}

	public static class BlockPair {
		public StoredBlock storedBlock;
	}

	/**
	 * Emulates receiving a valid block
	 */
	public static BlockPair createFakeBlock(BlockStore blockStore, StoredBlock previousStoredBlock, long version,
											long timeSeconds, int height, Transaction... transactions) {
		try {
			Block previousBlock = previousStoredBlock.getHeader();
			Address to = new ECKey().toAddress(previousBlock.getParams());
			Block b = previousBlock.createNextBlock(to, version, timeSeconds, height);
			// Coinbase tx was already added.
			for (Transaction tx : transactions) {
				tx.getConfidence().setSource(TransactionConfidence.Source.NETWORK);
				b.addTransaction(tx);
			}
			b.solve();
			BlockPair pair = new BlockPair();
			pair.storedBlock = previousStoredBlock.build(b);
			blockStore.put(pair.storedBlock);
			blockStore.setChainHead(pair.storedBlock);
			return pair;
		} catch (VerificationException | BlockStoreException e) {
			throw new RuntimeException(e); // Cannot happen.
		}
	}

	public static Block makeSolvedTestBlock(BlockStore blockStore, Address coinsTo) throws BlockStoreException {
		Block b = blockStore.getChainHead().getHeader().createNextBlock(coinsTo);
		b.solve();
		return b;
	}

	public static Block makeSolvedTestBlock(Block prev, Transaction... transactions) {
		Address to = new ECKey().toAddress(prev.getParams());
		Block b = prev.createNextBlock(to);
		// Coinbase tx already exists.
		for (Transaction tx : transactions) {
			b.addTransaction(tx);
		}
		b.solve();
		return b;
	}

}

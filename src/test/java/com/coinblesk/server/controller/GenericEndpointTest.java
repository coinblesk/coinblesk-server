/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.controller;

import com.coinblesk.json.v1.RefundTO;
import com.coinblesk.json.v1.SignTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.VerifyTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.Client;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utilTest.ServerCalls;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.store.BlockStoreException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

//http://www.soroushjp.com/2014/12/20/bitcoin-multisig-the-hard-way-understanding-raw-multisignature-bitcoin-transactions/
/**
 *
 * @author draft
 */
public class GenericEndpointTest extends CoinbleskTest {

	public final static long UNIX_TIME_MONTH = 60 * 60 * 24 * 30;
	public final static int LOCK_TIME_MONTHS = 3;

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private AppConfig appConfig;

	@Autowired
	private WalletService walletService;

	private static MockMvc mockMvc;

	private NetworkParameters params;

	@Before
	public void setUp() throws Exception {
		walletService.shutdown();
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
		walletService.init();
		params = appConfig.getNetworkParameters();
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testOldTime() throws Exception {
		Client client = new Client(params, mockMvc);
		Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
		Coin amountToRequest = Coin.valueOf(9876);
		Date now = new Date(1);
		Address merchantAddress = new ECKey().toAddress(params);
		Transaction tx = BitcoinUtils.createTx(params, client.outpoints(t), client.redeemScript(), client.p2shAddress(),
				merchantAddress, amountToRequest.getValue(), true);
		// test /prepare
		SignTO statusPrepare = ServerCalls.signServerCall(mockMvc, tx, client, now);
		Assert.assertFalse(statusPrepare.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, statusPrepare.type());
		// test /refund-p2sh
		RefundTO statusRefund = ServerCalls.refundServerCall(params, mockMvc, client.ecKey(), Collections.emptyList(),
				Collections.emptyList(), now, 0);
		Assert.assertFalse(statusRefund.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
		// test /complete-sign
		VerifyTO statusSign = ServerCalls.verifyServerCall(mockMvc, client.ecKey(), client.p2shAddress(),
				new Transaction(params), now);
		Assert.assertFalse(statusSign.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());

	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testNewTime() throws Exception {
		Client client = new Client(params, mockMvc);
		Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
		Coin amountToRequest = Coin.valueOf(9876);
		Date now = new Date(Long.MAX_VALUE / 2);
		Address merchantAddress = new ECKey().toAddress(params);
		Transaction tx = BitcoinUtils.createTx(params, client.outpoints(t), client.redeemScript(), client.p2shAddress(),
				merchantAddress, amountToRequest.getValue(), true);
		// test /prepare
		SignTO status = ServerCalls.signServerCall(mockMvc, tx, client, now);
		Assert.assertFalse(status.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, status.type());
		// test /refund-p2sh
		RefundTO statusRefund = ServerCalls.refundServerCall(params, mockMvc, client.ecKey(), Collections.emptyList(),
				Collections.emptyList(), now, 0);
		Assert.assertFalse(statusRefund.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
		// test /complete-sign
		VerifyTO statusSign = ServerCalls.verifyServerCall(mockMvc, client.ecKey(), client.p2shAddress(),
				new Transaction(params), now);
		Assert.assertFalse(statusSign.isSuccess());
		Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testWrongSignature() throws Exception {
		Client client = new Client(params, mockMvc);
		Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
		Coin amountToRequest = Coin.valueOf(9876);
		Date now = new Date();
		Address merchantAddress = new ECKey().toAddress(params);
		Transaction tx = BitcoinUtils.createTx(params, client.outpoints(t), client.redeemScript(), client.p2shAddress(),
				merchantAddress, amountToRequest.getValue(), true);
		// test /prepare
		SignTO prepareHalfSignTO = ServerCalls.signServerCallInput(tx, client.ecKey(), now);
		SerializeUtils.signJSON(prepareHalfSignTO, new ECKey());
		SignTO statusPrepare = ServerCalls.signServerCallOutput(mockMvc, prepareHalfSignTO);
		Assert.assertFalse(statusPrepare.isSuccess());
		Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusPrepare.type());
		// test /refund-p2sh
		RefundTO refundP2shTO = ServerCalls.refundServerCallInput(params, client.ecKey(), Collections.emptyList(),
				Collections.emptyList(), now, 0);
		SerializeUtils.signJSON(refundP2shTO, new ECKey());
		RefundTO statusRefund = ServerCalls.refundServerCallOutput(mockMvc, refundP2shTO);
		Assert.assertFalse(statusRefund.isSuccess());
		Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusRefund.type());
		// test /complete-sign
		VerifyTO completeSignTO = ServerCalls.verifyServerCallInput(client.ecKey(), client.p2shAddress(),
				new Transaction(params), now);
		SerializeUtils.signJSON(completeSignTO, new ECKey());
		VerifyTO statusSign = ServerCalls.verifyServerCallOutput(mockMvc, completeSignTO);
		Assert.assertFalse(statusSign.isSuccess());
		Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusSign.type());

	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testNotRegistered() throws Exception {
		Client client = new Client(params, mockMvc);
		Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
		Coin amountToRequest = Coin.valueOf(9876);
		Date now = new Date();
		Address merchantAddress = new ECKey().toAddress(params);
		Transaction tx = BitcoinUtils.createTx(params, client.outpoints(t), client.redeemScript(), client.p2shAddress(),
				merchantAddress, amountToRequest.getValue(), true);
		// test /prepare
		ECKey key = new ECKey();
		SignTO prepareHalfSignTO = ServerCalls.signServerCallInput(tx, key, now);
		SerializeUtils.signJSON(prepareHalfSignTO, key);
		SignTO status = ServerCalls.signServerCallOutput(mockMvc, prepareHalfSignTO);
		Assert.assertFalse(status.isSuccess());
		Assert.assertEquals(Type.KEYS_NOT_FOUND, status.type());
		// test /refund-p2sh
		RefundTO refundP2shTO = ServerCalls.refundServerCallInput(params, key, Collections.emptyList(),
				Collections.emptyList(), now, 0);
		SerializeUtils.signJSON(refundP2shTO, key);
		RefundTO statusRefund = ServerCalls.refundServerCallOutput(mockMvc, refundP2shTO);
		Assert.assertFalse(statusRefund.isSuccess());
		Assert.assertEquals(Type.KEYS_NOT_FOUND, statusRefund.type());
		// test /complete-sign
		VerifyTO completeSignTO = ServerCalls.verifyServerCallInput(key, client.p2shAddress(), new Transaction(params),
				now);
		SerializeUtils.signJSON(completeSignTO, key);
		VerifyTO statusSign = ServerCalls.verifyServerCallOutput(mockMvc, completeSignTO);
		Assert.assertFalse(statusSign.isSuccess());
		Assert.assertEquals(Type.KEYS_NOT_FOUND, statusSign.type());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testIdempotent() throws Exception {
		Client client = new Client(params, mockMvc);
		Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
		Coin amountToRequest = Coin.valueOf(9876);
		Date now = new Date();
		Address merchantAddress = new ECKey().toAddress(params);

		Transaction tx = BitcoinUtils.createTx(params, client.outpoints(t), client.redeemScript(), client.p2shAddress(),
				merchantAddress, amountToRequest.getValue(), true);
		// test /sign
		SignTO statusPrepare1 = ServerCalls.signServerCall(mockMvc, tx, client, now);
		Assert.assertTrue(statusPrepare1.isSuccess());
		// again -> results in same output
		SignTO statusPrepare2 = ServerCalls.signServerCall(mockMvc, tx, client, now);
		Assert.assertTrue(statusPrepare2.isSuccess());
		Assert.assertEquals(SerializeUtils.GSON.toJson(statusPrepare1), SerializeUtils.GSON.toJson(statusPrepare2));
		// option 2
		List<Pair<byte[], Long>> outpointCoinPair1 = convert(t.getOutputs());
		SignTO statusPrepare3 = ServerCalls.signServerCall(mockMvc, outpointCoinPair1, merchantAddress,
				amountToRequest.getValue(), client, now);
		Assert.assertTrue(statusPrepare3.isSuccess());
		// again -> results in same output
		List<Pair<byte[], Long>> outpointCoinPair2 = convert(t.getOutputs());
		SignTO statusPrepare4 = ServerCalls.signServerCall(mockMvc, outpointCoinPair2, merchantAddress,
				amountToRequest.getValue(), client, now);
		Assert.assertTrue(statusPrepare4.isSuccess());
		Assert.assertEquals(SerializeUtils.GSON.toJson(statusPrepare3), SerializeUtils.GSON.toJson(statusPrepare4));
		// now we have the sigs and we can fully sign the tx
		Transaction transaction = BitcoinUtils.createTx(params, convert2(t.getOutputs()), client.redeemScript(),
				client.p2shAddress(), merchantAddress, amountToRequest.getValue(), true);
		List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(transaction, client.redeemScript(),
				client.ecKey());
		List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(statusPrepare4.signatures());

		BitcoinUtils.applySignatures(transaction, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
		Coin fee = transaction.getFee();
		int len = transaction.unsafeBitcoinSerialize().length;
		System.out.println("tx len: " + len);
		Assert.assertEquals(30, Math.round(fee.getValue() / (double) len));
		// test /refund
		// create a refund with outpoint.
		TransactionOutput output = transaction.getOutput(1);
		List<Pair<TransactionOutPoint, Coin>> refundOutpoints = new ArrayList<>(1);
		refundOutpoints.add(new Pair<>(output.getOutPointFor(), output.getValue()));

		// now + 1min
		long lockTimeSeconds = (System.currentTimeMillis() / 1000) + 60;
		Transaction refundTx = BitcoinUtils.createRefundTx(params, refundOutpoints, client.redeemScript(),
				client.ecKey().toAddress(params), lockTimeSeconds);

		List<TransactionSignature> clientSigsRefund = BitcoinUtils.partiallySign(refundTx, client.redeemScript(),
				client.ecKey());

		RefundTO statusRefund1 = ServerCalls.refundServerCall(params, mockMvc, client.ecKey(), refundOutpoints,
				clientSigsRefund, now, lockTimeSeconds);
		Assert.assertTrue(statusRefund1.isSuccess());

		RefundTO statusRefund2 = ServerCalls.refundServerCall(params, mockMvc, client.ecKey(), refundOutpoints,
				clientSigsRefund, now, lockTimeSeconds);
		Assert.assertTrue(statusRefund1.isSuccess());
		Assert.assertEquals(SerializeUtils.GSON.toJson(statusRefund1), SerializeUtils.GSON.toJson(statusRefund2));

		// test /complete-sign
		VerifyTO statusVerify1 = ServerCalls.verifyServerCall(mockMvc, outpointCoinPair2, merchantAddress,
				amountToRequest.getValue(), client, SerializeUtils.serializeSignatures(clientSigs),
				SerializeUtils.serializeSignatures(serverSigs), now);
		Assert.assertTrue(statusVerify1.isSuccess());
		VerifyTO statusVerify2 = ServerCalls.verifyServerCall(mockMvc, outpointCoinPair2, merchantAddress,
				amountToRequest.getValue(), client, SerializeUtils.serializeSignatures(clientSigs),
				SerializeUtils.serializeSignatures(serverSigs), now);
		Assert.assertTrue(statusVerify2.isSuccess());
		Assert.assertEquals(SerializeUtils.GSON.toJson(statusVerify1), SerializeUtils.GSON.toJson(statusVerify2));
	}

	private Transaction sendFakeCoins(Coin amount, Address to)
			throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
		Transaction tx = FakeTxBuilder.createFakeTx(params, amount, to);
		BlockChain chain = walletService.blockChain();
		Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
		chain.add(block);
		return tx;
	}

	private static List<Pair<byte[], Long>> convert(List<TransactionOutput> outputs) {
		List<Pair<byte[], Long>> retVal = new ArrayList<>(outputs.size());
		for (TransactionOutput output : outputs) {
			retVal.add(new Pair<>(output.getOutPointFor().unsafeBitcoinSerialize(), output.getValue().getValue()));
		}
		return retVal;
	}

	private static List<Pair<TransactionOutPoint, Coin>> convert2(List<TransactionOutput> outputs) {
		List<Pair<TransactionOutPoint, Coin>> retVal = new ArrayList<>(outputs.size());
		for (TransactionOutput output : outputs) {
			retVal.add(new Pair<>(output.getOutPointFor(), output.getValue()));
		}
		return retVal;
	}
}

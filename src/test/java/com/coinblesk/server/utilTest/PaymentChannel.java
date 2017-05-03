package com.coinblesk.server.utilTest;

import com.coinblesk.bitcoin.TimeLockedAddress;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.*;

import static org.bitcoinj.core.Transaction.*;

/**
 * @author Sebastian Stephan
 */
public class PaymentChannel {
	private NetworkParameters params;
	private Transaction tx;
	private Address changeAddress;
	private ECKey privateKey, serverPubKey;
	private int fee = 350;
	private Map<Integer, TimeLockedAddress> inputToAddress = new HashMap<>();
	private Coin serverValue = Coin.ZERO;

	public PaymentChannel(NetworkParameters params, Address changeAddress, ECKey privateKey, ECKey serverPubKey) {
		this.params = params;
		this.changeAddress = changeAddress;
		this.privateKey = privateKey;
		this.serverPubKey = serverPubKey;
		this.tx = new Transaction(params);
	}

	public PaymentChannel setFee(int fee) {
		this.fee = fee;
		return this;
	}

	public PaymentChannel addInputs(TimeLockedAddress fromAddress, TransactionOutput... outputs) {
		for (TransactionOutput output : outputs) {
			tx.addInput(output);
			int inputIndex = tx.getInputs().size() - 1;
			inputToAddress.put(inputIndex, fromAddress);
			addFakeSignature(inputIndex, fromAddress.createRedeemScript());
		}
		return this;
	}

	public PaymentChannel addOutput(Address address, Coin value) {
		tx.addOutput(new TransactionOutput(params, tx, value, address));
		return this;
	}

	public PaymentChannel addToServerOutput(Long value) {
		serverValue = serverValue.plus(Coin.valueOf(value));
		return this;
	}

	public PaymentChannel addToServerOutput(Coin value) {
		serverValue = serverValue.plus(value);
		return this;
	}

	public PaymentChannel setServerOutput(Long value) {
		serverValue = Coin.valueOf(value);
		return this;
	}

	public PaymentChannel setServerOutput(Coin value) {
		serverValue = value;
		return this;
	}

	public Transaction buildTx() {
		System.out.println(tx.getInputSum());
		Transaction txCopy = new Transaction(params, tx.bitcoinSerialize());
		if (serverValue.isPositive())
			txCopy.addOutput(serverValue, serverPubKey.toAddress(params));
		Coin sumOfOutputs = txCopy.getOutputSum();
		txCopy.addOutput(new TransactionOutput(params, txCopy, Coin.ZERO, changeAddress));
		Coin changeValue = tx.getInputSum().minus(sumOfOutputs).minus(calculateFee(txCopy));
		if (changeValue.isNegative())
			throw new RuntimeException("Not enough coin to set transaction fee");
		txCopy.getOutput(txCopy.getOutputs().size() - 1).setValue(changeValue);
		signAllInputs(txCopy);
		return txCopy;
	}

	private Coin calculateFee(Transaction transaction) {
		return Coin.valueOf(transaction.bitcoinSerialize().length * fee);
	}

	private void signAllInputs(Transaction transaction) {
		for (int i = 0; i < transaction.getInputs().size(); i++) {
			Script redeemScript = inputToAddress.get(i).createRedeemScript();
			TransactionSignature sig = transaction.calculateSignature(i, privateKey, redeemScript, SigHash.ALL, false);
			transaction.getInput(i).setScriptSig(new ScriptBuilder().data(sig.encodeToBitcoin()).build());
		}
	}

	private void addFakeSignature(int index, Script redeemScript) {
		TransactionSignature sig = tx.calculateSignature(index, privateKey, redeemScript, SigHash.ALL, false);
		tx.getInput(index).setScriptSig(new ScriptBuilder()
			.data(sig.encodeToBitcoin())
			.data(new byte[73])
			.smallNum(1).data(redeemScript.getProgram())
			.build());
	}

}

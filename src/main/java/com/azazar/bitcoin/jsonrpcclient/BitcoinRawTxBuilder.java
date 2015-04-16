/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.azazar.bitcoin.jsonrpcclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 *
 * @author azazar
 */
public class BitcoinRawTxBuilder {

    public final IBitcoinRPC bitcoin;

    public BitcoinRawTxBuilder(IBitcoinRPC bitcoin) {
        this.bitcoin = bitcoin;
    }
    public LinkedHashSet<IBitcoinRPC.TxInput> inputs = new LinkedHashSet();
    public List<IBitcoinRPC.TxOutput> outputs = new ArrayList();

    private class Input extends IBitcoinRPC.BasicTxInput {

        public Input(String txid, int vout) {
            super(txid, vout);
        }

        public Input(IBitcoinRPC.TxInput copy) {
            this(copy.txid(), copy.vout());
        }

        @Override
        public int hashCode() {
            return txid.hashCode() + vout;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (!(obj instanceof IBitcoinRPC.TxInput))
                return false;
            IBitcoinRPC.TxInput other = (IBitcoinRPC.TxInput) obj;
            return vout == other.vout() && txid.equals(other.txid());
        }

    }
    public BitcoinRawTxBuilder in(IBitcoinRPC.TxInput in) {
        inputs.add(new Input(in.txid(), in.vout()));
        return this;
    }

    public BitcoinRawTxBuilder in(String txid, int vout) {
        in(new IBitcoinRPC.BasicTxInput(txid, vout));
        return this;
    }

    public BitcoinRawTxBuilder out(String address, double amount) {
        if (amount <= 0d)
            return this;
        outputs.add(new IBitcoinRPC.BasicTxOutput(address, amount));
        return this;
    }

    public BitcoinRawTxBuilder in(double value) throws BitcoinException {
        return in(value, 6);
    }

    public BitcoinRawTxBuilder in(double value, int minConf) throws BitcoinException {
        List<IBitcoinRPC.Unspent> unspent = bitcoin.listUnspent(minConf);
        double v = value;
        for (IBitcoinRPC.Unspent o : unspent) {
            if (!inputs.contains(new Input(o))) {
                in(o);
                v = BitcoinUtil.normalizeAmount(v - o.amount());
            }
            if (v < 0)
                break;
        }
        if (v > 0)
            throw new BitcoinException("Not enough bitcoins ("+v+"/"+value+")");
        return this;
    }

    private HashMap<String, IBitcoinRPC.RawTransaction> txCache = new HashMap<String, IBitcoinRPC.RawTransaction>();

    private IBitcoinRPC.RawTransaction tx(String txId) throws BitcoinException {
        IBitcoinRPC.RawTransaction tx = txCache.get(txId);
        if (tx != null)
            return tx;
        tx = bitcoin.getRawTransaction(txId);
        txCache.put(txId, tx);
        return tx;
    }

    public BitcoinRawTxBuilder outChange(String address) throws BitcoinException {
        return outChange(address, 0d);
    }

    public BitcoinRawTxBuilder outChange(String address, double fee) throws BitcoinException {
        double is = 0d;
        for (IBitcoinRPC.TxInput i : inputs)
            is = BitcoinUtil.normalizeAmount(is + tx(i.txid()).vOut().get(i.vout()).value());
        double os = fee;
        for (IBitcoinRPC.TxOutput o : outputs)
            os = BitcoinUtil.normalizeAmount(os + o.amount());
        if (os < is)
            out(address, BitcoinUtil.normalizeAmount(is - os));
        return this;
    }

    public String create() throws BitcoinException {
        return bitcoin.createRawTransaction(new ArrayList<IBitcoinRPC.TxInput>(inputs), outputs);
    }
    
    public String sign() throws BitcoinException {
        return bitcoin.signRawTransaction(create());
    }

    public String send() throws BitcoinException {
        return bitcoin.sendRawTransaction(sign());
    }

}

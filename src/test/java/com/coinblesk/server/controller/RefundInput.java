/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.controller;

import com.coinblesk.util.Pair;
import java.util.List;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.TransactionSignature;

/**
 *
 * @author draft
 */
public class RefundInput {

    private List<Pair<TransactionOutPoint, Coin>> clientOutpoint;
    private List<Pair<TransactionOutPoint, Coin>> merchantOutpoint;
    private List<TransactionSignature> clientSinatures;
    private Transaction fullTx;

    public List<Pair<TransactionOutPoint, Coin>> clientOutpoint() {
        return clientOutpoint;
    }
    
    public RefundInput clientOutpoint(List<Pair<TransactionOutPoint, Coin>> clientOutpoint) {
        this.clientOutpoint = clientOutpoint;
        return this;
    }

    public List<Pair<TransactionOutPoint, Coin>> merchantOutpoint() {
        return merchantOutpoint;
    }
    
    public RefundInput merchantOutpoint(List<Pair<TransactionOutPoint, Coin>> merchantOutpoint) {
        this.merchantOutpoint = merchantOutpoint;
        return this;
    }

    public List<TransactionSignature> clientSinatures() {
        return clientSinatures;
    }
    
    public RefundInput clientSinatures(List<TransactionSignature> clientSinatures) {
        this.clientSinatures = clientSinatures;
        return this;
    }

    public RefundInput fullTx(Transaction fullTx) {
        this.fullTx = fullTx;
        return this;
    }
    
    public Transaction fullTx() {
        return fullTx;
    }
}

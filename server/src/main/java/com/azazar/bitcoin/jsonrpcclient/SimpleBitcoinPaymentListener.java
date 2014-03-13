/*
 * Copyright (c) 2013, Mikhail Yevchenko. All rights reserved. PROPRIETARY/CONFIDENTIAL.
 */
package com.azazar.bitcoin.jsonrpcclient;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

/**
 *
 * @author Mikhail Yevchenko <m.ṥῥẚɱ.ѓѐḿởύḙ@azazar.com>
 */
public class SimpleBitcoinPaymentListener implements BitcoinPaymentListener {

    @Override
    public void block(String blockHash) {
    }

    @Override
    public void transaction(Transaction transaction) {
    }
    
}

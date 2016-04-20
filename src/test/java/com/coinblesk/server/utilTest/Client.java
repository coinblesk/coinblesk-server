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
package com.coinblesk.server.utilTest;

import com.coinblesk.json.KeyTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author draft
 */
public class Client {

    

    final private ECKey ecKey;
    final private ECKey ecKeyServer;
    final private Script p2shScript;
    final private Script redeemScript;
    final private Address p2shAddress;
    final private NetworkParameters params;

    public Client(NetworkParameters params, MockMvc mockMvc) throws Exception {
        this.params = params;
        this.ecKey = new ECKey();
        this.ecKeyServer = register(ecKey, mockMvc);
        this.p2shScript = createP2SHScript(ecKey, ecKeyServer);
        this.redeemScript = createRedeemScript(ecKey, ecKeyServer);
        this.p2shAddress = p2shScript.getToAddress(params);
    }

    public Client(NetworkParameters params, ECKey ecKeyClient, ECKey ecKeyServer) throws Exception {
        this.params = params;
        this.ecKey = ecKeyClient;
        this.ecKeyServer = ecKeyServer;
        this.p2shScript = createP2SHScript(ecKey, ecKeyServer);
        this.redeemScript = createRedeemScript(ecKey, ecKeyServer);
        this.p2shAddress = p2shScript.getToAddress(params);
    }

    public ECKey ecKey() {
        return ecKey;
    }

    public ECKey ecKeyServer() {
        return ecKeyServer;
    }

    public Script p2shScript() {
        return p2shScript;
    }

    public Script redeemScript() {
        return redeemScript;
    }

    public Address p2shAddress() {
        return p2shAddress;
    }

    private Script createP2SHScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        return BitcoinUtils.createP2SHOutputScript(2, keys);
    }

    public boolean clientFirst() {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKey);
        keys.add(ecKeyServer);
        Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);
        return BitcoinUtils.clientFirst(keys, ecKey);
    }

    private Script createRedeemScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);
        return BitcoinUtils.createRedeemScript(2, keys);
    }

    private ECKey register(ECKey ecKeyClient, MockMvc mockMvc) throws Exception {
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(
                status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        return ECKey.fromPublicOnly(status.publicKey());
    }
    
    public static Transaction sendFakeCoins(NetworkParameters params, Coin amount, Address to, int wait,
            BlockChain... chains) 
            throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
        Transaction tx = FakeTxBuilder.createFakeTx(params, amount, to);
        for(BlockChain chain:chains) {
            Block block = FakeTxBuilder.makeSolvedTestBlock(
                chain.getBlockStore().getChainHead().getHeader(), tx);
            chain.add(block);
        }
        //in case we need to wait for any kind of notification
        if(wait > 0) {
            Thread.sleep(wait);
        }
        return tx;
    }
    
    public List<Pair<byte[], Long>> outpoints(Transaction funding) {
        List<Pair<byte[], Long>> retVal = new ArrayList<>(funding.getOutputs().size());
        for(TransactionOutput output:funding.getOutputs()) {
            if(p2shAddress.equals(output.getAddressFromP2SH(params))) {
                retVal.add(new Pair<byte[], Long>(
                    output.getOutPointFor().unsafeBitcoinSerialize(), output.getValue().getValue()));
            }
        }
        return retVal;
    }
}

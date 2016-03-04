/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import com.coinblesk.json.KeyTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
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
    
    private static final Gson GSON;
    
    static {
         GSON = new GsonBuilder().create();
    }
    
    final private ECKey ecKey;
    final private ECKey ecKeyServer;
    final private Script p2shScript;
    final private Script redeemScript;
    final private Address p2shAddress;
    
    public Client(NetworkParameters params, MockMvc mockMvc) throws Exception {
        ecKey = new ECKey();
        ecKeyServer = register(ecKey, mockMvc);
        p2shScript = createP2SHScript(ecKey, ecKeyServer);
        redeemScript  = createRedeemScript(ecKey, ecKeyServer);
        p2shAddress = p2shScript.getToAddress(params);
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
        return ScriptBuilder.createP2SHOutputScript(2, keys);
    }
    
    private Script createRedeemScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
        return ScriptBuilder.createRedeemScript(2, keys);
    }
    
    private ECKey register(ECKey ecKeyClient, MockMvc mockMvc) throws Exception {
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        return ECKey.fromPublicOnly(status.publicKey()); 
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.utilTest;

import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.TransactionSignature;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author draft
 */
public class ServerCalls {

    public static VerifyTO verifyServerCall(MockMvc mockMvc,
            List<Pair<byte[], Long>> outpointCoinPair, Address to, long amount, Client client,
            List<TxSig> clientSigs,
            List<TxSig> serverSigs, Date date) throws UnsupportedEncodingException, Exception {
        VerifyTO cs = verifyServerCallInput(outpointCoinPair, to, amount, client.ecKey(), clientSigs,
                serverSigs, date);
        return verifyServerCallOutput(mockMvc, cs);
    }

    public static VerifyTO verifyServerCallInput(List<Pair<byte[], Long>> outpointCoinPair, Address to,
            long amount, ECKey client, List<TxSig> clientSigs, List<TxSig> serverSigs, Date date) throws Exception {
        VerifyTO prepareHalfSignTO = new VerifyTO()
                .outpointsCoinPair(outpointCoinPair)
                .amountToSpend(amount)
                .p2shAddressTo(to.toString())
                .clientPublicKey(client.getPubKey())
                .serverSignatures(serverSigs)
                .clientSignatures(clientSigs)
                .currentDate(date.getTime());
        SerializeUtils.signJSON(prepareHalfSignTO, client);
        return prepareHalfSignTO;
    }

    public static VerifyTO verifyServerCall(
            MockMvc mockMvc, ECKey client, Address p2shAddressTo, Transaction fullTx, Date now) throws UnsupportedEncodingException, Exception {
        VerifyTO cs = verifyServerCallInput(client, p2shAddressTo, fullTx, now);
        return verifyServerCallOutput(mockMvc, cs);
    }

    public static VerifyTO verifyServerCallInput(
            ECKey client, Address p2shAddressTo, Transaction fullTx, Date now) throws UnsupportedEncodingException, Exception {
        VerifyTO cs = new VerifyTO()
                .clientPublicKey(client.getPubKey())
                .transaction(fullTx.unsafeBitcoinSerialize())
                .currentDate(now.getTime());
        SerializeUtils.signJSON(cs, client);
        return cs;
    }

    public static VerifyTO verifyServerCallOutput(MockMvc mockMvc, VerifyTO cs) throws Exception {
        MvcResult res = mockMvc.perform(post("/v2/p/v").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(cs))).andExpect(
                status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), VerifyTO.class);
    }

    public static RefundTO refundServerCall(NetworkParameters params, MockMvc mockMvc, ECKey client,
            List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints,
            List<TransactionSignature> partiallySignedRefundClient, Date date, long lockTime) throws Exception {
        RefundTO refundP2shTO = refundServerCallInput(params, client, refundClientOutpoints,
                partiallySignedRefundClient, date, lockTime);
        return refundServerCallOutput(mockMvc, refundP2shTO);
    }

    public static RefundTO refundServerCallInput(NetworkParameters params, ECKey client,
            List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints,
            List<TransactionSignature> partiallySignedRefundClient, Date date, long lockTime) throws Exception {
        RefundTO refundP2shTO = new RefundTO()
                .clientPublicKey(client.getPubKey())
                .outpointsCoinPair(SerializeUtils.serializeOutPointsCoin(refundClientOutpoints))
                .clientSignatures(SerializeUtils.serializeSignatures(partiallySignedRefundClient))
                .refundSendTo(client.toAddress(params).toString())
                .lockTime(lockTime)
                .currentDate(date.getTime());
        SerializeUtils.signJSON(refundP2shTO, client);
        return refundP2shTO;
    }

    public static RefundTO refundServerCallOutput(MockMvc mockMvc, RefundTO refundP2shTO) throws Exception {
        MvcResult res = mockMvc.perform(post("/p/r").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(refundP2shTO)))
                .andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), RefundTO.class);
    }

    public static SignTO signServerCall(MockMvc mockMvc, List<Pair<byte[], Long>> outpointCoinPair, Address to,
            long amount, Client client, Date date) throws Exception {
        SignTO prepareHalfSignTO = signServerCallInput(outpointCoinPair, to, amount, client.ecKey(), date);
        return signServerCallOutput(mockMvc, prepareHalfSignTO);
    }

    public static SignTO signServerCallInput(List<Pair<byte[], Long>> outpointCoinPair, Address to,
            long amount, ECKey client, Date date) throws Exception {
        SignTO prepareHalfSignTO = new SignTO()
                .outpointsCoinPair(outpointCoinPair)
                .amountToSpend(amount)
                .p2shAddressTo(to.toString())
                .clientPublicKey(client.getPubKey())
                .currentDate(date.getTime());
        SerializeUtils.signJSON(prepareHalfSignTO, client);
        return prepareHalfSignTO;
    }

    public static SignTO signServerCall(MockMvc mockMvc, Transaction tx, Client client, Date date) throws Exception {
        SignTO prepareHalfSignTO = signServerCallInput(tx, client.ecKey(), date);
        return signServerCallOutput(mockMvc, prepareHalfSignTO);
    }

    public static SignTO signServerCallInput(Transaction tx, ECKey client, Date date) throws Exception {
        SignTO prepareHalfSignTO = new SignTO()
                .transaction(tx.unsafeBitcoinSerialize())
                .clientPublicKey(client.getPubKey())
                .currentDate(date.getTime());
        SerializeUtils.signJSON(prepareHalfSignTO, client);
        return prepareHalfSignTO;
    }

    public static SignTO signServerCallOutput(MockMvc mockMvc, SignTO prepareHalfSignTO) throws Exception {
        MvcResult res = mockMvc.perform(post("/v2/p/s").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(prepareHalfSignTO)))
                .andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), SignTO.class);
    }
}

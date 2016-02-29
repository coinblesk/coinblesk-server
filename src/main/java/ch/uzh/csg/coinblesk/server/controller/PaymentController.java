/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.TransactionService;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utils.Pair;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import java.util.ArrayList;

import java.util.LinkedHashMap;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 *
 */
@RestController
@RequestMapping(value = {"/payment", "/p"})
public class PaymentController {

    public final static int LOCK_TIME_DAYS = 2;

    private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private KeyService clientKeyService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private KeyService keyService;

    /**
     * Input is the KeyTO with the client public key. The server will create for
     * this client public key its own server keypair and return the server
     * public key, or indicate an error in KeyTO (or via status code). Make sure
     * to check for isSuccess(). Internally the server will hash the client
     * public key with SHA-256 (UUID) and clients need to identify themselfs
     * with this UUID for subsequent calls.
     *
     */
    @RequestMapping(value = {"/key-exchange", "/x"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public KeyTO keyExchange(@RequestBody KeyTO keyTO) {
        LOG.debug("Register clientHash for {}", keyTO.publicKey());
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final byte[] clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(clientPublicKey));
            keys.add(serverEcKey);
            //2-of-2 multisig
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressClient = script.getToAddress(params);

            final Pair<Boolean, Keys> retVal = clientKeyService.storeKeysAndAddress(clientPublicKey,
                    p2shAddressClient, serverEcKey.getPubKey(),
                    serverEcKey.getPrivKeyBytes());
            final KeyTO serverKeyTO = new KeyTO();
            if (retVal.element0()) {
                serverKeyTO.publicKey(serverEcKey.getPubKey());
                walletService.addWatching(script);
                return serverKeyTO.setSuccess();
            } else {
                serverKeyTO.publicKey(retVal.element1().serverPublicKey());
                return serverKeyTO.type(Type.KEY_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new KeyTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/balance", "/b"}, method = RequestMethod.GET,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public BalanceTO balance(@RequestBody KeyTO keyTO) {
        LOG.debug("Balance clientHash for {}", keyTO.publicKey());
        try {
            final List<ECKey> keys = clientKeyService.getPublicECKeysByClientPublicKey(keyTO.publicKey());
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Coin balance = walletService.balance(script);
            return new BalanceTO().balance(balance.value);

        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new BalanceTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/refund", "/r"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundTO refund(@RequestBody RefundTO refundTO) {
        LOG.debug("Refund for {}", refundTO.clientPublicKey());
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(refundTO.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new RefundTO().type(Type.KEYS_NOT_FOUND);
            }
            //this is how the client sees the tx
            final Transaction refundTransaction = new Transaction(params, refundTO.refundTransaction());
            //TODO: check client setting for locktime
            List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(refundTO.clientSignatures());
            
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            List<TransactionSignature> serverSigs = BitcoinUtils.partiallySign(refundTransaction, redeemScript, keys.get(1));
            BitcoinUtils.applySignatures(refundTransaction, redeemScript, clientSigs, serverSigs);
            refundTO.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
            //TODO: enable
            //refundTransaction.verify(); make sure those inputs are from the known p2sh address (min conf)
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            clientKeyService.addRefundTransaction(refundTO.clientPublicKey(), refundTx);
            return new RefundTO().setSuccess().refundTransaction(refundTx);
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/prepare", "/p"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public PrepareHalfSignTO prepareHalfSign(@RequestBody PrepareHalfSignTO prepareSignTO) {
        LOG.debug("Prepare half signed {}", prepareSignTO.clientPublicKey());
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(prepareSignTO.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new PrepareHalfSignTO().type(Type.KEYS_NOT_FOUND);
            }
            final ECKey serverKey = keys.get(1);
            final Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            final Coin amountToSpend = Coin.valueOf(prepareSignTO.amountToSpend());

            if (prepareSignTO.p2shAddressTo() == null || prepareSignTO.p2shAddressTo().isEmpty()) {
                return new PrepareHalfSignTO().type(Type.ADDRESS_EMPTY);
            }
            final Address p2shAddressTo = new Address(params, prepareSignTO.p2shAddressTo());

            if (!clientKeyService.containsP2SH(p2shAddressFrom)) {
                return new PrepareHalfSignTO().type(Type.ADDRESS_UNKNOWN);
            }

            //get all outputs from the BT network
            List<TransactionOutput> outputs = walletService.getOutputs(p2shAddressFrom);
            //in addition, get the outputs from previous TX, possibly not yet published in the BT network
            outputs.addAll(transactionService.approvedReceiving(params, p2shAddressFrom));
            outputs.removeAll(transactionService.approvedSpending(params, p2shAddressFrom));
            
            //TODO: check if the lock time is still high enough
            //now we have all valid outputs and we know its before the refund lock time gets valid. 
            //So we can sign this tx

            Transaction tx = BitcoinUtils.createTx(
                    params, outputs, p2shAddressFrom,
                    p2shAddressTo, amountToSpend.value);
            
            if (tx == null) {
                return new PrepareHalfSignTO().type(Type.NOT_ENOUGH_COINS);
            }
            
            if(!keyService.burnOutputFromNewTransaction(tx.getInputs())) {
                //double spending?
                //if we alreday have the outpoints for the tx, then we already
                //sent out the keys. The client can then use the keys, to submit
                //the transaction on its own. Thus, the server must keep track
                //of those outpoints
                //
                //if for any reason the client gets the keys, but fails to continue
                //in the payment process, its funds are marked as "burned". So the client
                //cannot use its fund with another merchant. Once the keys have been
                //issued, the client needs to finish the payment process.
                //
                //TODO: as a fallback, the burned outputs needs to be reseted. The server
                //sends back a re-topup transaction and the client signs it, the server
                //broadcasts the tx, thus, needing ~10min to make funds available it such a case.
                return new PrepareHalfSignTO().type(Type.DOUBLE_SPENDING);
            }


            final Script redeemScript = ScriptBuilder.createRedeemScript(2,keys);
            //sign the tx with the server keys
            List<TransactionSignature> serverTxSigs = BitcoinUtils.partiallySign(tx, redeemScript, serverKey);
            //TODO: mark these outputs as burned!! With the sig, the client can send it to 
            //the Bitcoin network itself.
            return new PrepareHalfSignTO()
                    .unsignedTransaction(tx.unsafeBitcoinSerialize())
                    .signatures(SerializeUtils.serializeSignatures(serverTxSigs));

        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new PrepareHalfSignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/refund-p2sh", "/f"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundP2shTO refundToP2SH(@RequestBody RefundP2shTO refundP2shTO) {
        LOG.debug("Prepare half signed {}", refundP2shTO.clientPublicKey());
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            //get client public key (identifier)
            final List<ECKey> keysClient = clientKeyService.getECKeysByClientPublicKey(refundP2shTO.clientPublicKey());
            final ECKey clientKey = keysClient.get(0);
            final ECKey serverClientKey = keysClient.get(1);
            final Script serverClientRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keysClient);
            final Address p2shAddressFrom = serverClientRedeemScript.getToAddress(params);
            //get merchant public key
            final List<ECKey> keysMerchant = clientKeyService.getECKeysByClientPublicKey(refundP2shTO.merchantPublicKey());
            final ECKey merchantKey = keysMerchant.get(0);
            final ECKey serverMerchantKey = keysMerchant.get(1);
            final Script serverMerchantRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keysMerchant);
            final Address p2shAddressTo = serverMerchantRedeemScript.getToAddress(params);

            final Transaction tx = new Transaction(params, refundP2shTO.unsignedTransaction());
            //TODO: this tx is unsigned, get the sigs and check it that we applied the signature! we want to be stateless

            //we now get from the client the outpoints for the refund tx (including hash)
            List<TransactionOutPoint> refundClientPoints = SerializeUtils.deserializeOutPoints(
                    params, refundP2shTO.refundClientOutpoints());
            //the client and the merchant have signed the refund tx, the client created the refund tx, 
            //the merchant and server using the outpoints to recreate the tx

            //Client refund is here, we can do a full refund
            List<TransactionSignature> clientRefundSignatures = SerializeUtils.deserializeSignatures(refundP2shTO.refundSignaturesClient());
            Pair<Transaction, List<TransactionSignature>> refundClientPair = createClientRefund(clientKey.toAddress(params),
                    serverClientKey, tx, p2shAddressFrom,
                    refundClientPoints, clientRefundSignatures, serverClientRedeemScript);
            if(refundClientPair == null) {
                 return new RefundP2shTO().type(Type.NOT_ENOUGH_COINS);
            }
            //unsignedRefund is now fully signed
            RefundP2shTO retVal = new RefundP2shTO();
            retVal.fullRefundTransactionClient(refundClientPair.element0().unsafeBitcoinSerialize());
            retVal.refundSignaturesClientServer(SerializeUtils.serializeSignatures(refundClientPair.element1()));

            //Merchant refund is here, we can do a full refund
            List<TransactionSignature> merchantRefundSignatures = SerializeUtils.deserializeSignatures(refundP2shTO.refundSignaturesMerchant());
            Transaction unsignedRefundMerchant = new Transaction(params, refundP2shTO.unsignedRefundMerchantTransaction());
            List<TransactionSignature> partiallySignedRefundServer = BitcoinUtils.partiallySign(
                unsignedRefundMerchant, serverMerchantRedeemScript, serverMerchantKey);
            BitcoinUtils.applySignatures(unsignedRefundMerchant, serverMerchantRedeemScript, merchantRefundSignatures, partiallySignedRefundServer);
            //TODO: check this tx!
            
            retVal.setSuccess().fullRefundTransactionMerchant(unsignedRefundMerchant.unsafeBitcoinSerialize());
            return retVal;

        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundP2shTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    private Pair<Transaction, List<TransactionSignature>> createClientRefund(final Address addressTo,
            final ECKey serverKey, final Transaction tx,
            final Address p2shAddress, final List<TransactionOutPoint> refundPoints,
            List<TransactionSignature> refundSignatures, final Script redeemScript) throws Exception {
        final NetworkParameters params = appConfig.getNetworkParameters();

        List<TransactionOutput> clientWalletOutputs = walletService.getOutputs(p2shAddress);
        List<TransactionOutput> clientMergedOutputs = BitcoinUtils.mergeOutputs(params,
                tx, clientWalletOutputs, p2shAddress);

        //for the unsiged refund we need the TransactionOutPoint and the value from the clientMergedOutputs, 
        //the client did the same operation as well
        LinkedHashMap<TransactionOutPoint, Coin> outputsToUseClient = BitcoinUtils.convertOutPoints(refundPoints, clientMergedOutputs);

        int lockTime = BitcoinUtils.lockTimeBlock(LOCK_TIME_DAYS, walletService.currentBlock());
        Transaction unsignedRefund = BitcoinUtils.generateUnsignedRefundTx(
                params, outputsToUseClient,
                addressTo,
                redeemScript, lockTime);

        if (unsignedRefund == null) {
            return null;
        }

        List<TransactionSignature> partiallySignedRefundServer = BitcoinUtils.partiallySign(
                unsignedRefund, redeemScript, serverKey);
        BitcoinUtils.applySignatures(unsignedRefund, redeemScript, refundSignatures, partiallySignedRefundServer);
        return new Pair(unsignedRefund, partiallySignedRefundServer);
    }

    @RequestMapping(value = {"/complete-sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public CompleteSignTO sign(@RequestBody CompleteSignTO signTO) {
        LOG.debug("Complete sign {}", signTO.clientPublicKey());
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            
            final List<ECKey> keysClient = clientKeyService.getECKeysByClientPublicKey(signTO.clientPublicKey());
            if (keysClient == null || keysClient.size() != 2) {
                return new CompleteSignTO().type(Type.KEYS_NOT_FOUND);
            }
            final Script serverClientRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keysClient);
            final Address p2shAddressFrom = serverClientRedeemScript.getToAddress(params);
           
            final List<ECKey> keysMerchant = clientKeyService.getECKeysByClientPublicKey(signTO.merchantPublicKey());
            if (keysMerchant == null || keysMerchant.size() != 2) {
                return new CompleteSignTO().type(Type.KEYS_NOT_FOUND);
            }
            final Script serverMerchantRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keysMerchant);
            final Address p2shAddressTo = serverMerchantRedeemScript.getToAddress(params);
            

            Transaction fullTx = new Transaction(params, signTO.fullSignedTransaction());
            //TODO: check fullTx, check client/server sigs
            transactionService.approveTx(fullTx, p2shAddressFrom, p2shAddressTo);
            
            //TODO: this could be removed here, but I haven't fully thought this through
            //for the moment its safer to remove the burned outputs once we see the 
            //tx in the blockchain
            //keyService.removeConfirmedBurnedOutput(fullTx.getInputs());
            
            //TODO: broadcast to network
            
            //TODO: now we can also check if the refund tx is valid
            return new CompleteSignTO().setSuccess();
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new CompleteSignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
}

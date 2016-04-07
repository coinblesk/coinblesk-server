package ch.uzh.csg.coinblesk.server.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.util.Pair;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.dao.AddressDAO;
import ch.uzh.csg.coinblesk.server.dao.KeyDAO;
import ch.uzh.csg.coinblesk.server.dao.RefundDAO;
import ch.uzh.csg.coinblesk.server.dao.TxDAO;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.Refund;
import ch.uzh.csg.coinblesk.server.entity.TimeLockedAddressEntity;
import ch.uzh.csg.coinblesk.server.entity.Tx;

/**
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@Service
public class KeyService {

    private final static Logger LOG = LoggerFactory.getLogger(KeyService.class);
    
    @Autowired
    private AppConfig appConfig;

    @Autowired
    private KeyDAO clientKeyDAO;
    
    @Autowired
    private AddressDAO addressDAO;
    
    @Autowired
    private TxDAO txDAO;
    
    @Autowired
    private RefundDAO refundDAO;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Transactional(readOnly = true)
    public Keys getByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public Keys getByClientPublicKey(final byte[] clientPublicKeyRaw) {
        return clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getPublicECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getPublicECKeysByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getPublicECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPublicOnly(keys.serverPublicKey()));
        return retVal;
    }
    
    @Transactional(readOnly = true)
    public List<ECKey> getECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getECKeysByClientPublicKey(clientPublicKeyRaw);
    }
    
    @Transactional(readOnly = true)
    public List<ECKey> getECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        if(keys == null) {
            return Collections.emptyList();
        }
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey()));
        return retVal;
    }

    @Transactional(readOnly = false)
    public Pair<Boolean, Keys> storeKeysAndAddress(final byte[] clientPublicKey, final Address p2shAdderss,
            final byte[] serverPublicKey, final byte[] serverPrivateKey) {
        if (clientPublicKey == null || p2shAdderss == null || serverPublicKey == null || serverPrivateKey == null ) {
            throw new IllegalArgumentException("null not excpected here");
        }
        
        byte[] p2shHash = p2shAdderss.getHash160();

        final Keys clientKey = new Keys()
                .clientPublicKey(clientPublicKey)
                .p2shHash(p2shHash)
                .serverPrivateKey(serverPrivateKey)
                .serverPublicKey(serverPublicKey);

        //need to check if it exists here, as not all DBs does that for us
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        if (keys != null) {
            return new Pair<>(false, keys);
        }

        clientKeyDAO.save(clientKey);
        return new Pair<>(true, clientKey);
    }
    
    /* SIGN ENDPOINT CODE ENDS HERE */
	
    @Transactional(readOnly = false)
	public TimeLockedAddressEntity storeTimeLockedAddress(Keys keys, TimeLockedAddress address) {
		if (address == null || keys == null) {
			throw new IllegalArgumentException("Address/keys must not be null");
		}
		if (keys.serverPrivateKey() == null || keys.serverPublicKey() == null || 
				keys.clientPublicKey() == null) {
			throw new IllegalArgumentException("Keys must not be null.");
		}
		if (address.getAddressHash() == null) {
			throw new IllegalArgumentException("AddressHash must not be null");
		}
		
		TimeLockedAddressEntity addressEntity = new TimeLockedAddressEntity();
		addressEntity
				.setLockTime(address.getLockTime())
				.setAddressHash(address.getAddressHash())
				.setRedeemScript(address.createRedeemScript().getProgram())
				.setTimeCreated(System.currentTimeMillis()/1000L)
				.setKeys(keys);
		
		TimeLockedAddressEntity result = addressDAO.save(addressEntity);
		return result;
	}
    
    public TimeLockedAddressEntity getTimeLockedAddressByAddressHash(byte[] addressHash) {
    	if (addressHash == null) {
    		throw new IllegalArgumentException("addressHash must not be null.");
    	}
    	return addressDAO.findTimeLockedAddressByAddressHash(addressHash);
    }
    
    public List<TimeLockedAddressEntity> getTimeLockedAddressesByClientPublicKey(byte[] publicKey) {
    	if (publicKey == null || publicKey.length <= 0) {
    		throw new IllegalArgumentException("publicKey must not be null");
    	}
    	return addressDAO.findTimeLockedAddressesByClientPublicKey(publicKey);
    }

	@Transactional(readOnly = true)
    public List<List<ECKey>> all() {
        final List<Keys> all = clientKeyDAO.findAll();
        final List<List<ECKey>> retVal = new ArrayList<>();
        for (Keys entity : all) {
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(entity.clientPublicKey()));
            keys.add(ECKey.fromPublicOnly(entity.serverPublicKey()));
            retVal.add(keys);
        }
        return retVal;
    }
    
    @Transactional(readOnly = false)
    public void addRefundTransaction(byte[] clientPublicKey, byte[] refundTransaction) {
       Refund refund = new Refund();
       refund.clientPublicKey(clientPublicKey);
       refund.refundTx(refundTransaction);
       refund.creationDate(new Date());
       refundDAO.save(refund);
    }
    
    @Transactional(readOnly = true)
    public List<Transaction> findRefundTransaction(NetworkParameters params, byte[] clientPublicKey) {
       List<Refund> refunds = refundDAO.findByClientPublicKey(clientPublicKey);
       List<Transaction> retVal = new ArrayList<>(refunds.size());
       for(Refund refund:refunds) {
           retVal.add(new Transaction(params, refund.refundTx()));
       }
       return retVal;
    }
    
    @Transactional(readOnly = true)
    public boolean containsP2SH(Address p2shAddress) {
        return clientKeyDAO.containsP2SH(p2shAddress.getHash160());
    }
    
    /* SIGN ENDPOINT CODE STARTS HERE */
    @Transactional(readOnly = false)
    public void addTransaction(byte[] clientPublicKey, byte[] serializedTransaction) {
        Tx transaction = new Tx();
        transaction.clientPublicKey(clientPublicKey);
        transaction.tx(serializedTransaction);
        transaction.creationDate(new Date());
        txDAO.save(transaction);
    }
    
    private final static long LOCK_THRESHOLD_MILLIS = 1000 * 60 * 60 * 4;
    
    @Transactional(readOnly = true)
    public List<TransactionOutPoint> burnedOutpoints(byte[] clientPublicKey) {
        final List<TransactionOutPoint> relevantOutpoints = new ArrayList<>();
        final List<Tx> clientTransactions = txDAO.findByClientPublicKey(clientPublicKey);
        for (Tx clientTransaction : clientTransactions) {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final Transaction storedTransaction = new Transaction(params, clientTransaction.tx());
            for(TransactionInput ti:storedTransaction.getInputs()) {
                relevantOutpoints.add(ti.getOutpoint());
            }
        }
        return relevantOutpoints;
    }
    
    @Transactional(readOnly = false)
    public boolean isTransactionInstant(byte[] clientPublicKey, Script redeemScript, Transaction fullTx) {
        //check if the funding (inputs) of fullTx are timelocked, so only for the 
        
        //here we get all signed, but we need only refund tx
        //TODO: store it better
        /*final List<Tx> clientTransactions = txDAO.findByClientPublicKey(clientPublicKey);
        Map<Sha256Hash, Transaction> refunds = new HashMap<>();
        for(Tx t:clientTransactions) {
           final Transaction storedTransaction = new Transaction(
                   appConfig.getNetworkParameters(), t.tx());
           if(storedTransaction.isTimeLocked()) {
               refunds.put(storedTransaction.getHash(), storedTransaction);
           }
        }
        
        //if no refund, locktime is forever
        long lockTime = 0;
        for(TransactionInput input: fullTx.getInputs()) {
            TransactionOutPoint point = input.getOutpoint();
            Transaction refund = refunds.get(point.getHash());
            if(refund != null) {
                //get the highest locktime, this will be the one that we need to check
                if(refund.getLockTime() > lockTime) {
                    lockTime = refund.getLockTime();
                    LOG.debug("locktime {} for {}", lockTime, refund.getHash());
                }
            } else {
                //we are good, if at least one input is not timelocked, so the client cannot spend it!
                lockTime = 0;
                break;
            }
        }
        
        //if locktime is smaller plus 4 hours, we cannot make an instant tx 
        if (lockTime > 0 && (lockTime * 1000) + LOCK_THRESHOLD_MILLIS > (System.currentTimeMillis())) {
            LOG.debug("there is a refund soon to be expired, cannot be for instant: {}", fullTx.getHash());
            return false;
        }*/
            
        return isTransactionInstant(clientPublicKey, redeemScript, fullTx, null);
    }

    private boolean isTransactionInstant(byte[] clientPublicKey, Script redeemScript, Transaction fullTx, Transaction requester) {
        long currentTime = System.currentTimeMillis();
        //fullTx can be null, we did not find a parent!
        if (fullTx == null) {
            LOG.debug("we did not find a parent transaction for {}", requester);
            return false;
        }
        
        //check if already approved
        List<Transaction> approved = transactionService.approvedTx2(appConfig.getNetworkParameters(), clientPublicKey);
        for(Transaction tx:approved) {
            if(tx.getHash().equals(fullTx.getHash())) {
                return true;
            }
        }
        
        if (fullTx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf()) {
            LOG.debug("The confidence of tx {} is good: {}", fullTx.getHash(), fullTx.getConfidence().getDepthInBlocks());
            return true;
        } else {
            final List<TransactionInput> relevantInputs = fullTx.getInputs();

            final List<Tx> clientTransactions = txDAO.findByClientPublicKey(clientPublicKey);

            // check double signing
            int signedInputCounter = 0;
            for (Tx clientTransaction : clientTransactions) {
                int relevantInputCounter = 0;
                for (TransactionInput relevantTransactionInput : relevantInputs) {
                    final NetworkParameters params = appConfig.getNetworkParameters();
                    final Transaction storedTransaction = new Transaction(params, clientTransaction.tx());
                    int storedInputCounter = 0;
                    for (TransactionInput storedTransactionInput : storedTransaction.getInputs()) {
                        if (storedTransactionInput.getOutpoint().toString().equals(relevantTransactionInput.getOutpoint().toString())) {
                            if (!storedTransaction.hashForSignature(storedInputCounter, redeemScript, Transaction.SigHash.ALL, false)
                                    .equals(fullTx.hashForSignature(relevantInputCounter, redeemScript, Transaction.SigHash.ALL, false))) {
                                long lockTime = storedTransaction.getLockTime() * 1000;
                                
                                if (lockTime > currentTime + LOCK_THRESHOLD_MILLIS) {
                                    continue;
                                } else {
                                    signedInputCounter++;
                                }
                            }
                        }
                        storedInputCounter++;
                    }
                    relevantInputCounter++;
                }
            }

            boolean areParentsInstant = false;
            for (TransactionInput relevantTransactionInput : relevantInputs) {
                TransactionOutput transactionOutput = relevantTransactionInput.getOutpoint().getConnectedOutput();
                //input may not be connected, it may be null
                if(transactionOutput == null) {
                    //TODO, figure out, why fullTx may not be connected, it is called after
                    //walletService.receivePending(fullTx);
                    transactionOutput = walletService.findOutputFor(relevantTransactionInput);
                    LOG.debug("This input is not connected! {}", relevantTransactionInput);
                    if(transactionOutput == null) {
                        LOG.debug("This input is still not connected! {}", relevantTransactionInput);
                        areParentsInstant = false;
                    break;
                    }
                }
                Transaction parentTransaction = transactionOutput.getParentTransaction();
                if (isTransactionInstant(clientPublicKey, redeemScript, parentTransaction, fullTx)) {
                    areParentsInstant = true;
                } else {
                    areParentsInstant = false;
                    break;
                }
            }
            LOG.debug("areParentsInstant {}", areParentsInstant);

            boolean isApproved = signedInputCounter == 0 && areParentsInstant;
            if(isApproved) {
                transactionService.approveTx2(fullTx, clientPublicKey, clientPublicKey);
            }
            return isApproved;
        }
    }
    /* SIGN ENDPOINT CODE ENDS HERE */
}

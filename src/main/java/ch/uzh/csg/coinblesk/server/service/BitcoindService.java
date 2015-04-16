package ch.uzh.csg.coinblesk.server.service;

import java.math.BigDecimal;
import java.util.Date;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoind;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayInTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayInTransactionUnverified;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutTransaction;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.Credentials;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinAcceptor;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.azazar.bitcoin.jsonrpcclient.ConfirmedPaymentListener;
import com.azazar.bitcoin.jsonrpcclient.IBitcoinRPC;
import com.azazar.bitcoin.jsonrpcclient.IBitcoinRPC.Transaction;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;

/**
 * Abstraction of the Bitcoind RPC Client interface
 *
 */
@Service
public class BitcoindService implements IBitcoind {
    
    private static Logger LOGGER = Logger.getLogger(BitcoindService.class);
    
    private static int keyPoolCounter = 0;
    public static boolean TESTING = false;
    
    private Credentials credentials;
    private boolean listenTransactions;
    
    @Autowired
    private IBitcoinRPC bitcoinRpcService;
    @Autowired
    private IPayInTransaction payInTransactionService;
    @Autowired
    private IPayInTransactionUnverified payInTransactionServiceUnverified;
    @Autowired
    private IPayOutTransaction payOutTransactionService;


    private BitcoinAcceptor incomingSmall;
    private BitcoinAcceptor incomingUnverified;
    private BitcoinAcceptor incomingBig;
    private BitcoinAcceptor outgoing;
    //
    private Thread incomingSmallThread;
    private Thread incomingUnverifiedThread;
    private Thread incomingBigThread;
    private Thread outgoingThread;

    @PostConstruct
    private void init() {
        backupWallet();
        if (isListenTransactions()) {
            // activates receivePayIn/Out Listener
            try {
                listenIncomingTransactions();
                listenOutgoingTransactions();
            } catch (BitcoinException e) {
                LOGGER.error("Bitcoind Exception: Couldn't initialize receivment of Bitcoin PayIN Transactions");
                e.printStackTrace();
            }
        }

    }

    @PreDestroy
    private void shutdown() {
        if (isListenTransactions()) {
            if (incomingSmall != null) {
                incomingSmall.stopAccepting();
                if (incomingSmallThread != null) {
                    incomingSmallThread.interrupt();
                }
            }
            if (incomingUnverified != null) {
                incomingUnverified.stopAccepting();
                if (incomingUnverifiedThread != null) {
                    incomingUnverifiedThread.interrupt();
                }
            }
            if (incomingBig != null) {
                incomingBig.stopAccepting();
                if (incomingBigThread != null) {
                    incomingBigThread.interrupt();
                }
            }

            if (outgoing != null) {
                outgoing.stopAccepting();
                if (outgoingThread != null) {
                    outgoingThread.interrupt();
                }
            }
        }
    }

    @Override
    public String sendCoins(String address, BigDecimal amount) throws BitcoinException {
        if (TESTING) {
            return "test-transaction-id";
        }
        try {
            bitcoinRpcService.unlockWallet(credentials.getBitcoindEncryptionKey(), 2);
        } catch (Exception e) {
            if (e.getMessage().contains("Error: Wallet is already unlocked.")) {
                // do nothing --> wallet is already unlocked
            } else
                throw e;
        }
        keyPoolCheck();
        return bitcoinRpcService.sendFrom(credentials.getBitcoindUsername(), address, amount.doubleValue());
    }

    @Override
    public boolean validateAddress(String address) throws BitcoinException {
        if (TESTING) {
            return offlineValidateAddress(address);
        } else {
            return bitcoinRpcService.validateAddress(address).isValid();
        }
    }

    @Override
    public boolean offlineValidateAddress(String address) throws BitcoinException {
        try {
            Address.getParametersFromAddress(address);
            return true;
        } catch (AddressFormatException e) {
            throw new BitcoinException(e);
        }
    }

    @Override
    public String getNewAddress() throws BitcoinException {
        keyPoolCheck();
        
        String newAddress = null;
        if(credentials.getBitcoindUsername() != null) {
            newAddress = bitcoinRpcService.getNewAddress(credentials.getBitcoindUsername());
        } else {
            newAddress = bitcoinRpcService.getNewAddress();
        }
        return newAddress;
    }

    @Override
    public void listenIncomingTransactions() throws BitcoinException {
        listenIncomingBigTransactions();
        listenIncomingUnverifiedTransactions();

        ConfirmedPaymentListener smallTx = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS) {

            @Override
            public void confirmed(IBitcoinRPC.Transaction transaction) {
                try {
                    if (transaction.category().equals("receive") && transaction.amount() <= Config.SMALL_TRANSACTION_LIMIT) {
                        LOGGER.info("Incoming small transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " + transaction.address());
                        payInTransactionService.create(transaction);
                    }
                } catch (UserAccountNotFoundException e) {
                    LOGGER.error("Couldn't allocate incoming transaction. Useraccount not found. Transaction: " + transaction.toString());
                }
            }

        };

        incomingSmall = new BitcoinAcceptor(bitcoinRpcService, smallTx, false);
        incomingSmallThread = new Thread(incomingSmall);
        incomingSmallThread.start();
    }

    @Override
    public void listenIncomingUnverifiedTransactions() throws BitcoinException {
        ConfirmedPaymentListener unverifiedTxListener = new ConfirmedPaymentListener(0) {
            @Override
            public void confirmed(Transaction transaction) {
                try {
                    if (transaction.category().equals("receive")) {
                        boolean unverified = false;
                        if (transaction.amount() <= Config.SMALL_TRANSACTION_LIMIT && transaction.confirmations() < Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS) {
                            unverified = true;
                        } else if (transaction.amount() > Config.SMALL_TRANSACTION_LIMIT && transaction.confirmations() < Config.MIN_CONFIRMATIONS_BIG_TRANSACTIONS) {
                            unverified = true;
                        }
                        if (unverified) {
                            LOGGER.info("Incoming unverified transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: "
                                    + transaction.address());
                            payInTransactionServiceUnverified.create(transaction);
                        }
                    }
                } catch (UserAccountNotFoundException e) {
                    LOGGER.error("Couldn't allocate incoming transaction. Useraccount not found. Transaction: " + transaction.toString());
                }
            }
        };
        incomingUnverified = new BitcoinAcceptor(bitcoinRpcService, unverifiedTxListener, false);
        incomingUnverifiedThread = new Thread(incomingUnverified);
        incomingUnverifiedThread.start();
    }

    @Override
    public void listenIncomingBigTransactions() throws BitcoinException {
        ConfirmedPaymentListener bigTxListener = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_BIG_TRANSACTIONS) {

            @Override
            public void confirmed(IBitcoinRPC.Transaction transaction) {
                try {
                    if (transaction.category().equals("receive")) {
                        if (transaction.amount() > Config.SMALL_TRANSACTION_LIMIT) {
                            LOGGER.info("Incoming big transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " + transaction.address());
                            payInTransactionService.create(transaction);
                        }
                    }
                } catch (UserAccountNotFoundException e) {
                    LOGGER.error("Couldn't allocate incoming transaction. Useraccount not found. Transaction: " + transaction.toString());
                }
            }

        };

        incomingBig = new BitcoinAcceptor(bitcoinRpcService, bigTxListener, false);
        incomingBigThread = new Thread(incomingBig);
        incomingBigThread.start();
    }

    @Override
    public void listenOutgoingTransactions() throws BitcoinException {
        ConfirmedPaymentListener outgoingTxListener = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS) {

            @Override
            public void confirmed(IBitcoinRPC.Transaction transaction) {
                if (transaction.category().equals("send")) {
                    LOGGER.info("Outgoing transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " + transaction.address());
                    payOutTransactionService.check(transaction);
                }
            }

        };

        outgoing = new BitcoinAcceptor(bitcoinRpcService, outgoingTxListener, true);
        outgoingThread = new Thread(outgoing);
        outgoingThread.start();
    }

    @Override
    public void backupWallet() {
        try {
            Date date = new Date();
            bitcoinRpcService.backupWallet(ServerProperties.getProperty("backup.dir") + "_" + date.toString());
            LOGGER.info("Successfully saved wallet backup.");
        } catch (BitcoinException e) {
            LOGGER.error("Saving wallet backup failed. ErrorMessage: " + e.getMessage());
        }
    }

    private void keyPoolCheck() throws BitcoinException {
        keyPoolCounter++;
        if (keyPoolCounter % 90 == 0) {
            try {
                bitcoinRpcService.unlockWallet(credentials.getBitcoindEncryptionKey(), 2);
            } catch (Exception e) {
                if (e.getMessage().equals("{\"result\":null,\"error\":{\"code\":-17,\"message\":\"Error: Wallet is already unlocked.\"},\"id\":\"1\"}")) {
                    // do nothing --> wallet is already unlocked
                } else
                    throw e;
            }
            bitcoinRpcService.keyPoolRefill();
            backupWallet();
        }
    }

    @Override
    public boolean isListenTransactions() {
        return listenTransactions;
    }

    @Override
    public void setListenTransactions(boolean listenTransactions) {
        this.listenTransactions = listenTransactions;
    }

    @Override
    public BigDecimal getAccountBalance() throws BitcoinException {
        BigDecimal accountBalance = new BigDecimal(Double.toString(bitcoinRpcService.getBalance(credentials.getBitcoindUsername(), Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS)));
        return accountBalance;
    }
    
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }
}

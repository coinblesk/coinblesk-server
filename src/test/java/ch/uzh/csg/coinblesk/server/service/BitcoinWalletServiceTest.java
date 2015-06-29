package ch.uzh.csg.coinblesk.server.service;

import java.io.File;
import java.io.FilenameFilter;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet.MissingSigsMode;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.signers.StatelessTransactionSigner;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.uzh.csg.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.DoubleSpendException;
import ch.uzh.csg.coinblesk.server.bitcoin.DummyPeerDiscovery;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class BitcoinWalletServiceTest {
    
    private class TestTransactionSigner extends StatelessTransactionSigner {

        final ServerSignatureRequestTransferObject txSigRequest;
        
        public TestTransactionSigner(ServerSignatureRequestTransferObject txSigRequest) {
            this.txSigRequest = txSigRequest;
        }
        
        @Override
        public boolean signInputs(ProposedTransaction propTx, KeyBag keyBag) {

            Transaction tx = propTx.partialTx;
            int numInputs = tx.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                TransactionInput txIn = tx.getInput(i);
                TransactionOutput txOut = txIn.getConnectedOutput();
                if (txOut == null) {
                    continue;
                }
                Script scriptPubKey = txOut.getScriptPubKey();

                try {
                    txIn.getScriptSig().correctlySpends(tx, i, txIn.getConnectedOutput().getScriptPubKey());
                    continue;
                } catch (Exception e) {
                    // Expected.
                }

                List<ChildNumber> childNumbers = propTx.keyPaths.get(scriptPubKey);
                int[] path = new int[childNumbers.size()];
                for(int j = 0; j < childNumbers.size(); j++) {
                    path[j] = childNumbers.get(j).getI();
                }
                txSigRequest.addIndexAndDerivationPath(i, path);

            }

            String serializedTx = Base64.getEncoder().encodeToString(tx.bitcoinSerialize());
            txSigRequest.setPartialTx(serializedTx);

            return true;
        }
        
        @Override
        public boolean isReady() {
            return true;
        }
        
    }


    /**
     * Network parameters. Must be the same as defined in test-context.xml!
     */
    private final static NetworkParameters params = UnitTestParams.get();
    
    private final static File TEST_DIR = new File(".");
    private final static String TEST_WALLET_PREFIX = "testwallet";

    @Autowired
    private BitcoinWalletService bitcoinWalletService;
    
    @Before
    public void setUp() {
        deleteTestWalletFiles();
    }
    
    @After
    public void tearDown() {
        deleteTestWalletFiles();
    }

    @Test
    public void testGetSerializedServerWatchingKey() {

        String watchingKey = bitcoinWalletService.getSerializedServerWatchingKey();
        Assert.assertNotNull(watchingKey);
        DeterministicKey key = DeterministicKey.deserializeB58(watchingKey, params);

        Assert.assertNotNull(key);
        Assert.assertTrue(key.isPubKeyOnly());
    }
    
    @Test(expected=InvalidTransactionException.class)
    public void testSignTxAndBroadcast_invalidRequest() throws Exception {
        ServerSignatureRequestTransferObject txSigReq = new ServerSignatureRequestTransferObject();
        txSigReq.setPartialTx("corrupted-partial-tx");
        boolean success = bitcoinWalletService.signTxAndBroadcast(txSigReq.getPartialTx(), txSigReq.getIndexAndDerivationPaths());
        Assert.assertFalse(success);
    }
    
    @Test
    public void testSignTxAndBroadcast() throws Exception {
        
        WalletAppKit otherAppKit = createAppKit();
        WalletAppKit clientAppKit = getClientAppKit();

        // add a custom transaction signer
        final ServerSignatureRequestTransferObject txSigRequest = new ServerSignatureRequestTransferObject();
        
        clientAppKit.wallet().addTransactionSigner(new TestTransactionSigner(txSigRequest));
        
        // "send" 50 BTC to the client
        Transaction tx = FakeTxBuilder.createFakeTx(params, Coin.FIFTY_COINS, clientAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS));
        FakeTxBuilder.BlockPair bp = FakeTxBuilder.createFakeBlock(clientAppKit.store(), tx);

        clientAppKit.wallet().receiveFromBlock(tx, bp.storedBlock, AbstractBlockChain.NewBlockType.BEST_CHAIN, 0);
        clientAppKit.wallet().notifyNewBestBlock(bp.storedBlock);
        clientAppKit.wallet().getTransaction(tx.getHash());
        
        // now the actual testing begins
        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req);
        
        // check if TX was successful
        boolean success = bitcoinWalletService.signTxAndBroadcast(txSigRequest.getPartialTx(), txSigRequest.getIndexAndDerivationPaths());
        Assert.assertTrue(success);
        
        // Try to sign the same transaction again
        boolean invalidTxThrown = false;
        try {
            bitcoinWalletService.signTxAndBroadcast(txSigRequest.getPartialTx(), txSigRequest.getIndexAndDerivationPaths());
        } catch(DoubleSpendException e) {
            invalidTxThrown = true;
        }
        Assert.assertTrue(invalidTxThrown);
        
    }
    
    private WalletAppKit getClientAppKit() {
        // set up a client wallet
        WalletAppKit clientAppKit = new WalletAppKit(params, TEST_DIR, TEST_WALLET_PREFIX + "_client");
        clientAppKit.setDiscovery(new DummyPeerDiscovery());
        clientAppKit.setBlockingStartup(false);
        clientAppKit.startAsync().awaitRunning();
        clientAppKit.wallet().allowSpendingUnconfirmedTransactions();
        
        // marry the wallet to the server
        String watchingKey = bitcoinWalletService.getSerializedServerWatchingKey();
        DeterministicKey key = DeterministicKey.deserializeB58(watchingKey, params);
        MarriedKeyChain marriedKeyChain = MarriedKeyChain.builder()
                .random(new SecureRandom())
                .followingKeys(key)
                .threshold(2)
                .build();

        clientAppKit.wallet().addAndActivateHDChain(marriedKeyChain);
        Address addr = clientAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        Assert.assertTrue(addr.isP2SHAddress());
        
        return clientAppKit;
    }
    
    private WalletAppKit createAppKit() {
        WalletAppKit clientAppKit = new WalletAppKit(params, TEST_DIR, TEST_WALLET_PREFIX);
        clientAppKit.setDiscovery(new DummyPeerDiscovery());
        clientAppKit.setBlockingStartup(false);
        clientAppKit.startAsync().awaitRunning();
        
        return clientAppKit;
    }
    
    
    /**
     * Deletes files of wallets that were created for testing
     */
    private void deleteTestWalletFiles() {
        File[] walletFiles = TEST_DIR.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(TEST_WALLET_PREFIX);
            }
        });
        for(File f : walletFiles) {
            f.delete();
        }
    }


}

package ch.uzh.csg.coinblesk.server.service;

import java.io.File;
import java.io.FilenameFilter;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.transaction.Transactional;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet.MissingSigsMode;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.signers.StatelessTransactionSigner;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.ExpectedDatabase;
import com.github.springtestdbunit.assertion.DatabaseAssertionMode;

import com.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidClientSignatureException;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.bitcoin.NotEnoughUnspentsException;
import ch.uzh.csg.coinblesk.server.bitcoin.ValidRefundTransactionException;
import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.DispatcherConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
@WebAppConfiguration
@ContextConfiguration(classes={DispatcherConfig.class})
public class BitcoinWalletServiceTest {

    private class TestTransactionSigner extends StatelessTransactionSigner {

        final ServerSignatureRequestTransferObject txSigRequest;

        /**
         * This is a test transaction signer for the (mocked) client.
         * 
         * @param txSigRequest
         */
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

                byte accountNumber = (byte) propTx.keyPaths.get(scriptPubKey).get(1).getI();
                int childNumber = propTx.keyPaths.get(scriptPubKey).get(2).getI();
                txSigRequest.addAccountNumber(accountNumber);
                txSigRequest.addChildNumber(childNumber);

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

    private final static String TEST_WALLET_PREFIX = "testwallet";
    private final static Random RND = new Random(42L);

    @Autowired
    private BitcoinWalletService bitcoinWalletService;
    
    @Autowired
	private AppConfig appConfig;

    @Before
    public void setUp() {
        deleteTestWalletFiles();
        bitcoinWalletService.init();
    }

    @After
    public void tearDown() {
        bitcoinWalletService.stop();
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

    @Test(expected = InvalidTransactionException.class)
    public void testSignTxAndBroadcast_invalidRequest() throws Exception {
        ServerSignatureRequestTransferObject txSigReq = new ServerSignatureRequestTransferObject();
        txSigReq.setPartialTx("corrupted-partial-tx");
        bitcoinWalletService.signAndBroadcastTx(txSigReq.getPartialTx(), txSigReq.getAccountNumbers(), txSigReq.getChildNumbers());
    }

    @Test
    public void testReceiveClienttransaction() throws Exception {

        WalletAppKit clientAppKit = getClientAppKit();
        WalletAppKit clientAppKit2 = getClientAppKit();

        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), clientAppKit, clientAppKit2, bitcoinWalletService.getAppKit());

        Assert.assertTrue(clientAppKit.wallet().currentReceiveAddress().isP2SHAddress());

        Assert.assertTrue(Coin.FIFTY_COINS.compareTo(clientAppKit.wallet().getBalance()) == 0);
        Assert.assertTrue(Coin.FIFTY_COINS.compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

        sendFakeCoins(Coin.COIN, clientAppKit2.wallet().currentReceiveAddress(), clientAppKit, clientAppKit2, bitcoinWalletService.getAppKit());

        WalletAppKit[] clients = new WalletAppKit[1];
        clients[0] = clientAppKit2;
        printWallets(clients, bitcoinWalletService.getAppKit());

        Assert.assertTrue(Coin.COIN.compareTo(clientAppKit2.wallet().getBalance()) == 0);
        Assert.assertTrue(Coin.FIFTY_COINS.add(Coin.COIN).compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

    }

    @Test
    public void testReceiveClienttransactionManyClients() throws Exception {

        int numClient = 3;
        int numTransactions = 9;

        WalletAppKit[] clients = new WalletAppKit[numClient];

        for (int i = 0; i < numClient; i++) {
            clients[i] = getClientAppKit();
        }

        for (int i = 0; i < numTransactions; i++) {
            sendFakeCoins(Coin.COIN, clients[RND.nextInt(numClient)].wallet().currentReceiveAddress(), bitcoinWalletService.getAppKit(), clients);
        }

        Assert.assertTrue(Coin.COIN.multiply(numTransactions).compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

    }


    @Test
    public void testSignTxAndBroadcast() throws Exception {

        WalletAppKit clientAppKit = getClientAppKit();
        WalletAppKit otherAppKit = createAppKit();

        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), bitcoinWalletService.getAppKit(), clientAppKit);

        Assert.assertTrue(Coin.FIFTY_COINS.compareTo(clientAppKit.wallet().getBalance()) == 0);
        Assert.assertTrue(Coin.FIFTY_COINS.compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

        // now the actual testing begins
        ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);

        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req);

        // check if TX was successful
        String base64encodedSignedTx = bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());
        
        // check if transaction can be deserialized
        Transaction tx = new Transaction(params, Base64.getDecoder().decode(base64encodedSignedTx));
        Assert.assertNotNull(tx);

        // Try to sign the same transaction again
        boolean invalidTxThrown = false;
        try {
            bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());
        } catch (InvalidTransactionException e) {
            invalidTxThrown = true;
        }
        Assert.assertTrue(invalidTxThrown);
        
        clientAppKit.stopAsync().awaitTerminated();

    }
    
    @Test(expected = InvalidClientSignatureException.class)
    public void testSignTxAndBroadcast_invalidClientSignature() throws Exception {

        WalletAppKit clientAppKit = getClientAppKit();
        WalletAppKit otherAppKit = createAppKit();

        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), bitcoinWalletService.getAppKit(), clientAppKit);

        ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);

        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req);
        
        String originalTx = txSigRequest.getPartialTx();
        Transaction tx = new Transaction(params, Base64.getDecoder().decode(txSigRequest.getPartialTx()));
        Script redeemScript = new Script(tx.getInput(0).getScriptSig().getChunks().get(tx.getInput(0).getScriptSig().getChunks().size() - 1).data);
        
        // create a valid signature but with a wrong key and replace the correct signature with this signature
        DeterministicKey wrongKey = new DeterministicKeyChain(new SecureRandom()).getKey(KeyPurpose.RECEIVE_FUNDS);
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        TransactionSignature wrongSig = new TransactionSignature(wrongKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script dummyP2SHScript = ScriptBuilder.createP2SHOutputScript(new byte[20]);
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(null, redeemScript);
        inputScript = dummyP2SHScript.getScriptSigWithSignature(inputScript, wrongSig.encodeToBitcoin(), 0);
        tx.getInput(0).setScriptSig(inputScript);

        txSigRequest.setPartialTx(Base64.getEncoder().encodeToString(tx.bitcoinSerialize()));
        
        Assert.assertNotEquals(originalTx, txSigRequest.getPartialTx());

        bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

    }

    @Test(expected = NotEnoughUnspentsException.class)
    public void testSignTxAndBroadcast_notEnoughUnspents() throws Exception {

        WalletAppKit clientAppKit = getUnregisteredClientAppKit();
        WalletAppKit otherAppKit = createAppKit();

        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), bitcoinWalletService.getAppKit(), clientAppKit);

        Assert.assertTrue(Coin.FIFTY_COINS.compareTo(clientAppKit.wallet().getBalance()) == 0);
        Assert.assertFalse(Coin.FIFTY_COINS.compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

        // now the actual testing begins
        ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);

        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req);

        // check if TX was successful
        bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

    }

    @Test
    public void testSignRefundTx() throws Exception {
        WalletAppKit otherAppKit = createAppKit();
        WalletAppKit clientAppKit = getClientAppKit();

        // add a custom transaction signer
        final ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);
        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), bitcoinWalletService.getAppKit(), clientAppKit);

        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

        // test with a time locked tx
        SendRequest req2 = SendRequest.to(receiveAddr, Coin.COIN);
        req2.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        System.out.println("lolrofl");
        System.out.println(clientAppKit.wallet().getTransactionPool(Pool.UNSPENT));
        TransactionOutput output = clientAppKit.wallet().getTransactionPool(Pool.UNSPENT).values().iterator().next().getOutput(0);
        req2.tx.addInput(output);
        req2.tx.getInput(0).setSequenceNumber(0);
        req2.tx.setLockTime(1000);
        clientAppKit.wallet().completeTx(req2);

        // check if TX was successful
        String refundTxBase64 = bitcoinWalletService.signRefundTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());
        Assert.assertNotNull(refundTxBase64);
        Transaction refundTx = new Transaction(params, Base64.getDecoder().decode(refundTxBase64));
        Assert.assertTrue(refundTx.isTimeLocked());

    }

    @Test
    // @DatabaseSetup("classpath:emptyDataSet.xml") //TODO: load empty database
    // or else test will fail
    public void testSignRefundTx_NotYetValidRefundTx() throws Exception {
        WalletAppKit otherAppKit = createAppKit();
        WalletAppKit clientAppKit = getClientAppKit();

        // add a custom transaction signer
        final ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);
        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), clientAppKit, bitcoinWalletService.getAppKit());

        long refundTxLockTime = 50L;

        // test with a time locked tx
        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        TransactionOutput output = clientAppKit.wallet().getTransactionPool(Pool.UNSPENT).values().iterator().next().getOutput(0);
        req.tx.addInput(output);
        req.tx.getInput(0).setSequenceNumber(0);
        req.tx.setLockTime(refundTxLockTime);
        clientAppKit.wallet().completeTx(req);

        String partialTxBase64 = txSigRequest.getPartialTx();

        bitcoinWalletService.signRefundTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

        txSigRequest.clear();
        SendRequest req2 = SendRequest.to(receiveAddr, Coin.CENT);
        req2.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

        // refund transaction is not valid yet, so this should be allowed
        clientAppKit.wallet().completeTx(req2);
        Assert.assertNotEquals(partialTxBase64, txSigRequest.getPartialTx());
        bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

    }

    @Test(expected = ValidRefundTransactionException.class)
    public void testSignRefundTx_validRefundTx() throws Exception {
        WalletAppKit otherAppKit = createAppKit();
        WalletAppKit clientAppKit = getClientAppKit();

        // add a custom transaction signer
        final ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);
        sendFakeCoins(Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), clientAppKit, bitcoinWalletService.getAppKit());

        long refundTxLockTime = 50L;

        // test with a time locked tx
        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN);
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        TransactionOutput output = clientAppKit.wallet().getTransactionPool(Pool.UNSPENT).values().iterator().next().getOutput(0);
        req.tx.addInput(output);
        req.tx.getInput(0).setSequenceNumber(0);
        req.tx.setLockTime(refundTxLockTime);
        clientAppKit.wallet().completeTx(req);

        bitcoinWalletService.signRefundTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

        txSigRequest.clear();

        // now add some blocks, this should stop the server from signing this
        // input
        bitcoinWalletService.setCurrentBlockHeight(refundTxLockTime + 1);

        SendRequest req2 = SendRequest.to(receiveAddr, Coin.CENT);
        req2.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req2);

        bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());

    }

    private ServerSignatureRequestTransferObject getSigRequest(WalletAppKit appKit) {
        final ServerSignatureRequestTransferObject txSigRequest = new ServerSignatureRequestTransferObject();
        appKit.wallet().addTransactionSigner(new TestTransactionSigner(txSigRequest));
        return txSigRequest;
    }

    private void sendFakeCoins(Coin amount, Address receiveAddr, WalletAppKit... appKits) {

        try {
            Transaction tx = FakeTxBuilder.createFakeTx(params, amount, receiveAddr);
            Block block = FakeTxBuilder.makeSolvedTestBlock(appKits[1].store().getChainHead().getHeader(), tx);

            for (WalletAppKit appKit : appKits) {
                // "send" BTC to the client
                appKit.chain().add(block);
            }
            Thread.sleep(100);
        } catch (VerificationException | PrunedException | BlockStoreException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendFakeCoins(Coin amount, Address receiveAddr, WalletAppKit serverKit, WalletAppKit[] appKits) {
        WalletAppKit[] appKits2 = new WalletAppKit[appKits.length + 1];
        System.arraycopy(appKits, 0, appKits2, 0, appKits.length);
        appKits2[appKits.length] = serverKit;
        sendFakeCoins(amount, receiveAddr, appKits2);
    }

    private WalletAppKit getClientAppKit() throws Exception {
        return getClientAppKit(true);
    }

    private WalletAppKit getUnregisteredClientAppKit() throws Exception {
        return getClientAppKit(false);
    }

    private WalletAppKit getClientAppKit(boolean registerWithServer) throws Exception {

        // set up a client wallet
        WalletAppKit clientAppKit = new WalletAppKit(params, appConfig.getConfigDir().getFile(), TEST_WALLET_PREFIX + "_client" + RND.nextInt());
        // set dummy discovery, should be fixed in latest bitcoinj
        clientAppKit.setDiscovery(new PeerDiscovery() {
			@Override
			public void shutdown() {}
			@Override
			public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {return new InetSocketAddress[0];}
		});
        clientAppKit.setBlockingStartup(false);
        clientAppKit.startAsync().awaitRunning();
        clientAppKit.wallet().allowSpendingUnconfirmedTransactions();

        // marry the server to the client
        String watchingKey = bitcoinWalletService.getSerializedServerWatchingKey();
        DeterministicKey key = DeterministicKey.deserializeB58(watchingKey, params);
        MarriedKeyChain marriedKeyChain = MarriedKeyChain.builder().random(new SecureRandom()).followingKeys(key).threshold(2).build();
        clientAppKit.wallet().addAndActivateHDChain(marriedKeyChain);

        // marry the client to the server
        if (registerWithServer) {
            bitcoinWalletService.addWatchingKey(clientAppKit.wallet().getWatchingKey().serializePubB58(params));
        }

        Address addr = clientAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        Assert.assertTrue(addr.isP2SHAddress());

        return clientAppKit;
    }

	private WalletAppKit createAppKit() {

		WalletAppKit clientAppKit = null;

		clientAppKit = new WalletAppKit(params, appConfig.getConfigDir().getFile(), TEST_WALLET_PREFIX + RND.nextInt());

		// set dummy discovery, should be fixed in latest bitcoinj
		clientAppKit.setDiscovery(new PeerDiscovery() {
			@Override
			public void shutdown() {
			}

			@Override
			public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
				return new InetSocketAddress[0];
			}
		});
		clientAppKit.setBlockingStartup(false);
		clientAppKit.startAsync().awaitRunning();

		return clientAppKit;
	}

    /**
     * Deletes files of wallets that were created for testing
     */
	private void deleteTestWalletFiles() {
		File[] walletFiles;
		walletFiles = appConfig.getConfigDir().getFile().listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(TEST_WALLET_PREFIX);
			}
		});
		for (File f : walletFiles) {
			f.delete();
		}

	}

    private void printWallets(WalletAppKit[] clients, WalletAppKit server) {

        System.out.println("*****   CLIENTS    *****");
        for (WalletAppKit client : clients) {
            System.out.println(client.wallet());
        }
        System.out.println("*****   SERVER    *****");
        System.out.println(bitcoinWalletService.getAppKit().wallet());
    }
    
    @Test
    @Transactional
    @ExpectedDatabase(value = "classpath:DbUnitFiles/addedTransaction.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    public void testAddDoubleSpendSpentOutputs() throws Exception {
    	WalletAppKit clientAppKit = getClientAppKit();
    	Transaction tx = FakeTxBuilder.createFakeTx(params, Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress());
    	System.err.println(tx);
    	bitcoinWalletService.getSpentOutputDao().addOutput(tx);
    	
    	Assert.assertTrue(bitcoinWalletService.getSpentOutputDao().isDoubleSpend(tx));
    	Transaction tx2 = FakeTxBuilder.createFakeTx(params, Coin.FIFTY_COINS, clientAppKit.wallet().currentReceiveAddress(), new Address(params, new byte[20]))[1];
    	System.err.println(tx2);
    	Assert.assertFalse(bitcoinWalletService.getSpentOutputDao().isDoubleSpend(tx2));
    }
    
    @Test
    @Transactional
    @ExpectedDatabase(value = "classpath:DbUnitFiles/emptyDataSet.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    @DatabaseSetup("classpath:DbUnitFiles/oneSpentOutput.xml")
    public void testExpireSpentOutputs1() throws Exception {
    	bitcoinWalletService.getSpentOutputDao().removeOldEntries(1);
    }
    
    @Test
    @Transactional
    @ExpectedDatabase(value = "classpath:DbUnitFiles/oneSpentOutputResult.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    @DatabaseSetup("classpath:DbUnitFiles/twoSpentOutput.xml")
    public void testExpireSpentOutputs2() throws Exception {
    	bitcoinWalletService.getSpentOutputDao().removeOldEntries(1);
    	DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	Date date = formatter.parse("2040-05-31 22:15:52");
    	bitcoinWalletService.getSpentOutputDao().removeOldEntries(date, 1);
    }
    
    @Test
    @Transactional
    @ExpectedDatabase(value = "classpath:DbUnitFiles/emptyDataSet.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    @DatabaseSetup("classpath:DbUnitFiles/twoSpentOutput.xml")
    public void testExpireSpentOutputs3() throws Exception {
    	bitcoinWalletService.getSpentOutputDao().removeOldEntries(1);
    	DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	Date date = formatter.parse("2040-05-31 22:15:53");
    	bitcoinWalletService.getSpentOutputDao().removeOldEntries(date, 1);
    }

}

package ch.uzh.csg.coinblesk.server.service;

import java.io.File;
import java.io.FilenameFilter;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

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
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.signers.StatelessTransactionSigner;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.DeterministicKeyChain;
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
import com.google.common.collect.Lists;

import com.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.DispatcherConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
@WebAppConfiguration
@ContextConfiguration(classes={DispatcherConfig.class})
public class Playground3 {

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
                
                System.out.println("PARTIALLY SIGNED");
                System.out.println(txIn.getScriptSig());

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
    public void testSignTxAndBroadcast() throws Exception {
        
        WalletAppKit clientAppKit = getClientAppKit();
        WalletAppKit otherAppKit = createAppKit();
        
        int coins = 7;

        for(int i = 0; i < coins; i++) {
            sendFakeCoins(Coin.COIN, clientAppKit.wallet().freshReceiveAddress(), bitcoinWalletService.getAppKit(), clientAppKit);
        }

        Assert.assertTrue(Coin.COIN.multiply(coins).compareTo(clientAppKit.wallet().getBalance()) == 0);
        Assert.assertTrue(Coin.COIN.multiply(coins).compareTo(bitcoinWalletService.getAppKit().wallet().getBalance()) == 0);

        // now the actual testing begins
        ServerSignatureRequestTransferObject txSigRequest = getSigRequest(clientAppKit);

        Address receiveAddr = otherAppKit.wallet().currentAddress(KeyPurpose.RECEIVE_FUNDS);
        SendRequest req = SendRequest.to(receiveAddr, Coin.COIN.multiply(coins - 1));
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        clientAppKit.wallet().completeTx(req);

        // check if TX was successful
        String base64encodedSignedTx = bitcoinWalletService.signAndBroadcastTx(txSigRequest.getPartialTx(), txSigRequest.getAccountNumbers(), txSigRequest.getChildNumbers());
        
        Transaction tx = new Transaction(params, Base64.getDecoder().decode(base64encodedSignedTx));
        
        System.out.println("lolrofl");
        System.out.println(DatatypeConverter.printHexBinary(tx.getInput(0).getOutpoint().bitcoinSerialize()));
        System.out.println(DatatypeConverter.printHexBinary(tx.getInput(1).getOutpoint().bitcoinSerialize()));
        System.out.println(DatatypeConverter.printHexBinary(tx.getInput(0).getOutpoint().getHash().getBytes()));

        System.out.println(tx.getInput(0).getOutpoint());

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


}

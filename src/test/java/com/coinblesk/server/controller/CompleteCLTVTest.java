package com.coinblesk.server.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FilenameFilter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.bitcoin.AddressCoinSelector;
import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.config.SecurityConfig;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.common.io.Files;


@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({
	DependencyInjectionTestExecutionListener.class,
	TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {
		BeanConfig.class, 
		SecurityConfig.class})
@WebAppConfiguration
public class CompleteCLTVTest {
	
	private static final Logger Log = LoggerFactory.getLogger(CompleteCLTVTest.class);
	
	@Rule
    public ExpectedException thrown = ExpectedException.none();
	
	private final String TEST_WALLET_PREFIX = "test-wallet";
	
    @Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;
    
    @Autowired
    private AppConfig appConfig;
    private NetworkParameters params;
    private File tempDir;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private KeyService keyService;
    
    private Client client;
    private Client merchant;

    
    @Before
    public void setUp() throws Exception {
    	tempDir = Files.createTempDir();
    	
        walletService.shutdown();
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webAppContext)
                .addFilter(springSecurityFilterChain)
                .build();
        
        params = appConfig.getNetworkParameters();
        walletService.init();
        client = new Client(appConfig.getNetworkParameters(), mockMvc);
        merchant = new Client(appConfig.getNetworkParameters(), mockMvc);
        client.initWallet(tempDir, TEST_WALLET_PREFIX + "_client");
        
        fundClient(client.getChangeAddress());
    }

    @After
    public void tearDown() {
    	walletService.shutdown();
    	client.shutdownWallet();
    	
		File[] walletFiles = tempDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(TEST_WALLET_PREFIX);
			}
		});
		for (File wf : walletFiles) {
			wf.delete();
		}
		tempDir.delete();
    }
    
    @Test
    public void testReceiveFunds() throws Exception {
    	Transaction tx = fundClient(client.getChangeAddress());
    	
    	Map<Sha256Hash, Transaction> txUnspent;
    	
    	txUnspent = walletService.verifiedTransactions(params);
    	assertTrue(txUnspent.containsKey(tx.getHash()));
    	
    	txUnspent = client.wallet().getTransactionPool(Pool.UNSPENT);
    	assertTrue(txUnspent.containsKey(tx.getHash()));
    }
    
    @Test
    /* provide 2 signatures -> spending should succeed */
    public void testSpend_BeforeLockTimeExpiry() throws Exception {
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	
    	
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(clientSigs.size() > 0);
    	
    	SignTO signTO = client.signTxByServer(tx, clientSigs);
    	assertNotNull(signTO);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.signatures());
    	assertTrue(serverSigs.size() > 0);
    	assertEquals(serverSigs.size(), tx.getInputs().size());
    	
    	
    	assertEquals(clientSigs.size(), serverSigs.size());
    	
    	client.applySignatures(tx, clientSigs, serverSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
    
    @Test
    /* provide 2 signatures, but in reversed order, should fail */
    public void testSpend_BeforeLockTimeExpiry_ReversedSignatures() throws Exception {
    	thrown.expect(ScriptException.class);
    	thrown.expectMessage("Script failed OP_CHECKSIGVERIFY"); // checksigverify is used for the server sig (before client)
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	SignTO signTO = client.signTxByServer(tx, clientSigs);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.signatures());
    	
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size() && clientSigs.size() == serverSigs.size());
    	
    	// Note: reversed signature lists
    	client.applySignatures(tx, serverSigs, clientSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
    
    @Test
    /* spending with single sig of client is possible if locktime and seqNr is set properly */
    public void testSpend_AfterLockTimeExpiry_SingleSig() throws Exception {
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	TimeLockedAddress address = TimeLockedAddress.fromRedeemScript(client.getAllAddresses().get(client.getChangeAddress()));
    	tx.setLockTime(address.getLockTime());
    	tx.getInputs().forEach(ti -> ti.setSequenceNumber(0));

    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size());
    	
    	// provide only single sig.
    	client.applySignatures(tx, clientSigs);
    	client.verifyTx(tx);

    	fakeBroadcast(tx);
    }
    
    @Test
    /* spending with single sig of server should not be possible */
    public void testSpend_AfterLockTimeExpiry_SingleServerSig() throws Exception {
    	thrown.expect(ScriptException.class);
    	/* last checksig operation will fail and push false onto the stack */
    	thrown.expectMessage("P2SH script execution resulted in a non-true stack"); 
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	TimeLockedAddress address = TimeLockedAddress.fromRedeemScript(client.getAllAddresses().get(client.getChangeAddress()));
    	tx.setLockTime(address.getLockTime());
    	tx.getInputs().forEach(ti -> ti.setSequenceNumber(0));

    	// we must send signatures to server in order to sign.
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	SignTO signTO = client.signTxByServer(tx, clientSigs);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.signatures());
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == serverSigs.size());
    	
    	// provide only single sig, but not from client!
    	client.applySignatures(tx, serverSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
    
    @Test
	/* spending with single sig of client fails without lockTime */
	public void testSpend_AfterLockTimeExpiry_SingleSig_LockTimeTooSmall() throws Exception {
		thrown.expect(ScriptException.class);
		thrown.expectMessage("Locktime requirement not satisfied"); /* tx lock time does not match CLTV argument */
		
		Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
		
		TimeLockedAddress address = TimeLockedAddress.fromRedeemScript(client.getAllAddresses().get(client.getChangeAddress()));
		tx.setLockTime(address.getLockTime() - 1);
		tx.getInputs().forEach(ti -> ti.setSequenceNumber(0));
	
		List<TransactionSignature> clientSigs = client.signTxByClient(tx);
		assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size());
		
		// provide only single sig.
		client.applySignatures(tx, clientSigs);
		client.verifyTx(tx);
		
		fakeBroadcast(tx);
	}

	@Test
    /* spending with single sig of client fails without lockTime */
    public void testSpend_AfterLockTimeExpiry_SingleSig_MissingLockTime() throws Exception {
    	thrown.expect(ScriptException.class);
    	thrown.expectMessage("Locktime requirement type mismatch"); /* tx lock time is 0 ("blockheight"), but provided is a  lock time in seconds */
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	// nLockTime of Tx not set!
    	tx.getInputs().forEach(ti -> ti.setSequenceNumber(0));

    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size());
    	
    	// provide only single sig.
    	client.applySignatures(tx, clientSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
    
    @Test
    /* spending with single sig of client fails without seqNr */
    public void testSpend_AfterLockTimeExpiry_SingleSig_MissingSeqNr() throws Exception {
    	thrown.expect(ScriptException.class);
    	thrown.expectMessage("Transaction contains a final transaction input for a CHECKLOCKTIMEVERIFY script.");
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);

    	
    	TimeLockedAddress address = TimeLockedAddress.fromRedeemScript(client.getAllAddresses().get(client.getChangeAddress()));
    	tx.setLockTime(address.getLockTime());
    	// seqNr not set!

    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size());
    	
    	// provide only single sig.
    	client.applySignatures(tx, clientSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
    
    @Test
    /* spending with single sig of client fails without seqNr */
    public void testSpend_AfterLockTimeExpiry_SingleSig_MissingSeqNrAndLockTime() throws Exception {
    	thrown.expect(ScriptException.class);
    	thrown.expectMessage("Locktime requirement type mismatch"); /* we have set a lockTime in seconds, lockTime 0 is "blockheight" */
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);

    	// nLockTime not set!
    	// seqNr not set!

    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(tx.getInputs().size() > 0 && tx.getInputs().size() == clientSigs.size());
    	
    	// provide only single sig.
    	client.applySignatures(tx, clientSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }

	@Test
    public void testSpend_BeforeLockTime_MultipleInputs() throws Exception {
    	for (int i = 0; i < 5; ++i) {
    		client.createTimeLockedAddress();
    		fundClient(client.getChangeAddress());
    		Thread.sleep(500);
    	}
    	
    	for (Map.Entry<Address, Coin> b : client.getBalanceByAddresses().entrySet()) {
    		Log.info("Address balance: {}", b.getValue().toFriendlyString());
    	}
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.multiply(3).value);
    	
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	SignTO signTO = client.signTxByServer(tx, clientSigs);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.signatures());
    	
    	assertEquals(clientSigs.size(), serverSigs.size());
    	client.applySignatures(tx, clientSigs, serverSigs);
    	client.verifyTx(tx);
    	
    	fakeBroadcast(tx);
    }
	
	
	
    /*******************************
     * 
     * BLOCKCHAIN implementation
     * 
     *******************************/
	
    private Transaction fundClient(Address addressTo) throws Exception {
    	Transaction tx = sendFakeCoins(params, Coin.COIN, addressTo);
    	assertNotNull(tx);
    	return tx;
    }
	
    private Transaction sendFakeCoins(NetworkParameters params, Coin amount, Address to) throws Exception {
        Transaction tx = FakeTxBuilder.createFakeTx(params, amount, to);
        fakeBroadcast(tx);
        Thread.sleep(250);
        return tx;
    }
    
    private void fakeBroadcast(Transaction tx) throws Exception {
    	fakeBroadcast(tx, walletService.blockChain(), client.blockChain(), merchant.blockChain());
    }
    
    private static void fakeBroadcast(Transaction tx, BlockChain... blockChain) throws Exception {
    	// tx is serialized for each blockChain such because adding to chain and wallet alters flags and pointers.
    	final NetworkParameters params = tx.getParams();
    	final byte[] serializedTx = tx.bitcoinSerialize();
		for (BlockChain chain : blockChain) {
			if (chain == null) {
				continue;
			}
			
			Block blockHead = chain.getBlockStore().getChainHead().getHeader();
			Transaction txCopy = new Transaction(params, serializedTx);
			Block block = FakeTxBuilder.makeSolvedTestBlock(blockHead, txCopy);
			chain.add(block);
		}
	}
    
    
    /**
     * TODO Testcases
     * - double spend
     * - instant test: 
     * (1) before/after time lock (one and multiple inputs)
     * (2) parent not instant / instant 
     * (3) by confidence
     */

    
    
    /*******************************
     * 
     * CLIENT implementation
     * 
     *******************************/
	
	private static class Client {
		private final ECKey clientKey, serverKey;
		
		private Map<Address, byte[]> addressesToRedeemScripts;
		private Address mostRecentAddress;
		
		private final NetworkParameters params;
		private MockMvc mockMvc;
		
		private WalletAppKit clientAppKit;
		
		public Client(NetworkParameters params, MockMvc mockMvc) throws Exception {
			this.params = params;
			this.mockMvc = mockMvc;
			this.clientKey = new ECKey();
			this.addressesToRedeemScripts = new HashMap<>();
			
			TimeLockedAddress timeLockedAddress = createTimeLockedAddress();
			this.serverKey = ECKey.fromPublicOnly(timeLockedAddress.getServerPubKey());
		}
		
		public Map<Address, Coin> getBalanceByAddresses() {
			// this is not really efficient, we could it do in 1 iteration!
			Map<Address, Coin> balance = new HashMap<>();
	    	for (Address addr : getAllAddresses().keySet()) {
	    		Coin addressBalance = wallet().getBalance(new AddressCoinSelector(addr, params));
	    		balance.put(addr, addressBalance);
	    	}
	    	return balance;
		}
		
		public void applySignatures(Transaction tx, List<TransactionSignature> clientSigs,
													List<TransactionSignature> serverSigs) {
			// apply
	    	for (int tiIndex = 0; tiIndex < tx.getInputs().size(); ++tiIndex) {
	    		TransactionInput ti = tx.getInput(tiIndex);
	    		TransactionSignature clientSig = clientSigs.get(tiIndex);
	    		TransactionSignature serverSig = serverSigs.get(tiIndex);
	    		
	    		Address outputAddr = ti.getConnectedOutput().getAddressFromP2SH(params);
	    		byte[] redeemScript = addressesToRedeemScripts.get(outputAddr);
	    		Script scriptsig = TimeLockedAddress.createScriptSig(redeemScript, false, clientSig, serverSig);
	    		ti.setScriptSig(scriptsig);
	    	}  
		}
		
		public void applySignatures(Transaction tx, List<TransactionSignature> clientSigs) {
			// apply
			for (int tiIndex = 0; tiIndex < tx.getInputs().size(); ++tiIndex) {
				TransactionInput ti = tx.getInput(tiIndex);
				TransactionSignature clientSig = clientSigs.get(tiIndex);

				Address outputAddr = ti.getConnectedOutput().getAddressFromP2SH(params);
				byte[] redeemScript = addressesToRedeemScripts.get(outputAddr);
				Script scriptsig = TimeLockedAddress.createScriptSig(redeemScript, true, clientSig);
				ti.setScriptSig(scriptsig);
			} 
		}		
		
		public void verifyTx(Transaction tx) {
			// throws if error
			for (int tiIndex = 0; tiIndex < tx.getInputs().size(); ++tiIndex) {
				TransactionInput ti = tx.getInput(tiIndex);
				Script scriptsig = ti.getScriptSig();
				scriptsig.correctlySpends(tx, tiIndex, ti.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
			}
		}

		private void initWallet(File walletDir, String walletPrefix) {
	        clientAppKit = new WalletAppKit(params, walletDir, walletPrefix);
			clientAppKit.setDiscovery(new PeerDiscovery() {
				@Override
				public void shutdown() {
				}

				@Override
				public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
					return new InetSocketAddress[0];
				}
			});
	        clientAppKit.setBlockingStartup(false);
	        clientAppKit.startAsync().awaitRunning();
	        
	        for (Address address : addressesToRedeemScripts.keySet()) {
	        	wallet().addWatchedAddress(address);
	        }
		}
		
		public void shutdownWallet() {
			if (clientAppKit != null) {
				try {
					clientAppKit.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);
				} catch (TimeoutException e) {
					// ignore timeout
				}
			}
		}
		
		public Wallet wallet() {
			return clientAppKit != null ? clientAppKit.wallet() : null;
		}
		
		public BlockChain blockChain() {
			return clientAppKit != null ? clientAppKit.chain() : null;
		}
		
		public Address getChangeAddress() {
			return mostRecentAddress;
		}
		
		public Map<Address, byte[]> getAllAddresses() {
			return addressesToRedeemScripts;
		}

		public TimeLockedAddress createTimeLockedAddress() throws Exception {
			TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
					.currentDate(System.currentTimeMillis())
					.publicKey(clientKey.getPubKey());
			SerializeUtils.signJSON(requestTO, clientKey);
	        MvcResult res = mockMvc
	        		.perform(
	        				post(PaymentControllerTest.URL_CREATE_TIME_LOCKED_ADDRESS)
	        				.secure(true)
	        				.contentType(MediaType.APPLICATION_JSON)
	        				.content(SerializeUtils.GSON.toJson(requestTO)))
	        		.andExpect(status().isOk())
	        		.andReturn();
	        TimeLockedAddressTO response = SerializeUtils.GSON
	        		.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
	        
	        TimeLockedAddress timeLockedAddress = response.timeLockedAddress();
	        Address address = timeLockedAddress.getAddress(params);
	        addressesToRedeemScripts.put(address, timeLockedAddress.createRedeemScript().getProgram());
	        mostRecentAddress = address;
	        
	        if (wallet() != null) {
	        	wallet().addWatchedAddress(address);
	        }
	        return timeLockedAddress;
		}
		
		public SignTO signTxByServer(Transaction tx, List<TransactionSignature> clientSigs) throws Exception {
			SignTO signTO = new SignTO()
					.currentDate(System.currentTimeMillis())
					.publicKey(clientKey.getPubKey())
					.transaction(tx.unsafeBitcoinSerialize())
					.signatures(SerializeUtils.serializeSignatures(clientSigs))
					.setSuccess();
			SerializeUtils.signJSON(signTO, clientKey);
			
			MvcResult res = mockMvc
	        		.perform(post(PaymentControllerTest.URL_SIGN_VERIFY).secure(true)
	        				.contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(signTO)))
	        		.andExpect(status().isOk())
	        		.andReturn();
			SignTO response = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), SignTO.class);
			return response;
		}
		
		public List<TransactionSignature> signTxByClient(Transaction tx) throws Exception {
			List<TransactionSignature> clientSigs = new ArrayList<>();
	    	for (int tiIndex = 0; tiIndex < tx.getInputs().size(); ++tiIndex) {
	    		Address outputAddr = tx.getInput(tiIndex).getConnectedOutput().getAddressFromP2SH(params);
	    		byte[] redeemScript = addressesToRedeemScripts.get(outputAddr);
	    		TransactionSignature sig = tx.calculateSignature(tiIndex, 
	    				clientKey, redeemScript, 
	    				Transaction.SigHash.ALL, false);
	    		clientSigs.add(sig);
	    	}
	    	return clientSigs;
		}
		
		public ECKey getClientKey() {
			return clientKey;
		}

		public ECKey getServerKey() {
			return serverKey;
		}

	}
	
}

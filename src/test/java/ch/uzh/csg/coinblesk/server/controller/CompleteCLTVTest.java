package ch.uzh.csg.coinblesk.server.controller;

import static org.junit.Assert.*;
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
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.script.Script;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.common.io.Files;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utilTest.TestBean;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({
	DependencyInjectionTestExecutionListener.class,
	TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {
		TestBean.class, 
		BeanConfig.class, 
		SecurityConfig.class})
@WebAppConfiguration
public class CompleteCLTVTest {
	
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
    
    private Transaction fundClient(Address addressTo) throws Exception {
    	Transaction tx = PrepareTest.sendFakeCoins(params, Coin.COIN, 
    			addressTo, 
    			walletService.blockChain(), client.blockChain());
    	assertNotNull(tx);
    	return tx;
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
    
//    @Test
//    public void testSpend_BeforeLockTimeExpiry() throws Exception {
//    	List<TransactionOutput> unspent = client.wallet().getUnspents();
//    	Transaction spendTx = new Transaction(params);
//    	for (TransactionOutput to : unspent) {
//    		spendTx.addInput(to);
//    	}
//    	spendTx.addOutput(spendTx.getInputSum(), merchant.getChangeAddress());
//    	
//    	SignTO signTO = client.signTxByServer(spendTx);
//    	assertNotNull(signTO);
//    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.serverSignatures());
//    	assertTrue(serverSigs.size() > 0);
//    	assertEquals(serverSigs.size(), spendTx.getInputs().size());
//    	
//    	List<TransactionSignature> clientSigs = new ArrayList<>();
//    	for (int tiIndex = 0; tiIndex < spendTx.getInputs().size(); ++tiIndex) {
//    		TransactionSignature sig = spendTx.calculateSignature(tiIndex, 
//    				client.getClientKey(), client.getChangeAddress().createRedeemScript(), 
//    				Transaction.SigHash.ALL, false);
//    		clientSigs.add(sig);
//    	}
//    	assertTrue(clientSigs.size() > 0);
//    	assertEquals(clientSigs.size(), serverSigs.size());
//    	
//    	// apply
//    	for (int tiIndex = 0; tiIndex < spendTx.getInputs().size(); ++tiIndex) {
//    		TransactionInput ti = spendTx.getInput(tiIndex);
//    		TransactionSignature clientSig = clientSigs.get(tiIndex);
//    		TransactionSignature serverSig = serverSigs.get(tiIndex);
//    		Script scriptsig = client.getTimeLockedAddress().createScriptSigBeforeLockTime(clientSig, serverSig);
//    		ti.setScriptSig(scriptsig);
//    		
//    		// throws if error
//    		scriptsig.correctlySpends(spendTx, tiIndex, ti.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
//    	}   	
//    }
    
    @Test
    public void testSpend_BeforeLockTimeExpiry() throws Exception {
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
    	
    	SignTO signTO = client.signTxByServer(tx);
    	assertNotNull(signTO);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.serverSignatures());
    	assertTrue(serverSigs.size() > 0);
    	assertEquals(serverSigs.size(), tx.getInputs().size());
    	
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(clientSigs.size() > 0);
    	assertEquals(clientSigs.size(), serverSigs.size());
    	
    	client.applySignatures(tx, clientSigs, serverSigs);
    	
    	client.verifyTx(tx);
    }
    
    @Test
    public void testSpend_BeforeLockTime_MultipleInputs() throws Exception {
    	for (int i = 0; i < 5; ++i) {
    		client.createTimeLockedAddress();
    		fundClient(client.getChangeAddress());
    		Thread.sleep(500);
    	}
    	
    	for (Address a : client.getAllAddresses().keySet()) {
	    	Coin addressBalance = client.wallet().getBalance(new CoinSelector() {
				@Override
				public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
					Coin valueGathered = Coin.ZERO;
					List<TransactionOutput> gathered = new ArrayList<>();
					for (TransactionOutput to : candidates) {
						if (to.isAvailableForSpending() && to.getAddressFromP2SH(params).equals(a)) {
							valueGathered = valueGathered.add(to.getValue());
							gathered.add(to);
						}
					}
					CoinSelection selection = new CoinSelection(valueGathered, gathered);
					return selection;
				}
			});
	    	System.out.println("Balance - Address=" + a + ", balance=" + addressBalance.value);
    	}
    	
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.multiply(3).value);
    	
    	SignTO signTO = client.signTxByServer(tx);
    	assertNotNull(signTO);
    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.serverSignatures());
    	assertTrue(serverSigs.size() > 0);
    	assertEquals(serverSigs.size(), tx.getInputs().size());
    	
    	List<TransactionSignature> clientSigs = client.signTxByClient(tx);
    	assertTrue(clientSigs.size() > 0);
    	assertEquals(clientSigs.size(), serverSigs.size());
    	
    	client.applySignatures(tx, clientSigs, serverSigs);
    	
    	client.verifyTx(tx);
    	
    	  for(BlockChain chain:new BlockChain[]{client.clientAppKit.chain()}) {
              Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
              chain.add(block);
          }
    	
    	
    	for (Address a : client.getAllAddresses().keySet()) {
	    	Coin addressBalance = client.wallet().getBalance(new CoinSelector() {
				@Override
				public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
					Coin valueGathered = Coin.ZERO;
					List<TransactionOutput> gathered = new ArrayList<>();
					for (TransactionOutput to : candidates) {
						if (to.isAvailableForSpending() && to.getAddressFromP2SH(params).equals(a)) {
							valueGathered = valueGathered.add(to.getValue());
							gathered.add(to);
						}
					}
					CoinSelection selection = new CoinSelection(valueGathered, gathered);
					return selection;
				}
			});
	    	System.out.println("Balance - Address=" + a + ", balance=" + addressBalance.value);
    	}
    	
    }
    
    
    @Test
    public void testSpend_BeforeLockTimeExpiry_OnlyClientSig() throws Exception {
    	Transaction tx = BitcoinUtils.createTx(
						    			params, 
						    			client.wallet().getUnspents(), 
						    			client.getAllAddresses().keySet(), 
						    			client.getChangeAddress(), 
						    			merchant.getChangeAddress(), 
						    			Coin.COIN.divide(2).value);
				tx.setLockTime(TimeLockedAddress.fromRedeemScript(client.getAllAddresses().get(client.getChangeAddress())).getLockTime());
				tx.getInputs().forEach(ti -> ti.setSequenceNumber(0));
				List<TransactionSignature> clientSigs = client.signTxByClient(tx);
				
				client.applySignatures(tx, clientSigs);
				
				client.verifyTx(tx);
				
				for(BlockChain chain:new BlockChain[]{client.clientAppKit.chain()}) {
		              Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
		              chain.add(block);
		          }
				
    }
    
    @Test
    public void testSpendAfterLockTime() {
    	
    }
    
    @Test
    public void testSpend_AfterLockTime_NoSeqNr() {
    	
    }
    
    @Test
    public void testSpend_AfterLockTime_NoLockTime() {
    	
    }
    
//    @Test
//    public void testReceiveFunds_temp() throws Exception {
////    	Transaction tx = PrepareTest.sendFakeCoins(params, Coin.COIN, client.getTimeLockedAddress().getAddress(params), 
////    			walletService.blockChain(), clientAppKit.chain());
////    	
//    	List<TransactionOutput> unspent = client.wallet().getUnspents();
//    	Transaction t2 = new Transaction(params);
//    	for (TransactionOutput to : unspent) {
//    		t2.addInput(to);
//    	}
//    	t2.addOutput(Coin.COIN, merchant.getChangeAddress());
//    	
////    	SignTO signTO = client.signTx(t2);
////    	List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(signTO.serverSignatures());
////    	
////    	TransactionSignature clientSig = t2.calculateSignature(0, client.getClientKey(), client.getTimeLockedAddress().createRedeemScript(), Transaction.SigHash.ALL, false);
////    	
////    	int i = 0;
////    	for (TransactionInput ti : t2.getInputs()) {
////    		Script scriptsig = client.getTimeLockedAddress().createScriptSigBeforeLockTime(clientSig, serverSigs.get(0));
////    		ti.setScriptSig(scriptsig);
////    		scriptsig.correctlySpends(t2, i, ti.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
////    		++i;
////    	}
//    	
//    	
//    	t2.setLockTime(client.getTimeLockedAddress().getLockTime());
//    	for (TransactionInput ti : t2.getInputs()) {
//    		ti.setSequenceNumber(0);
//    	}
//    	TransactionSignature clientSig = t2.calculateSignature(0, client.getClientKey(), client.getTimeLockedAddress().createRedeemScript(), Transaction.SigHash.ALL, false);
//    	int i = 0;
//    	for (TransactionInput ti : t2.getInputs()) {
//    		Script scriptsig = client.getTimeLockedAddress().createScriptSigAfterLockTime(clientSig);
//    		ti.setScriptSig(scriptsig);
//    		scriptsig.correctlySpends(t2, i, ti.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
//    		++i;
//    	}
//    	
//    	System.out.println(t2.toString());
//    }
    
    
    
    
    
    
    
    
    
    
    
    
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
			this.serverKey = ECKey.fromPublicOnly(timeLockedAddress.getServicePubKey());
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
			KeyTO keyTO = new KeyTO().publicKey(clientKey.getPubKey());
	        MvcResult res = mockMvc
	        		.perform(post("/payment/createTimeLockedAddress").secure(true)
	        				.contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO)))
	        		.andExpect(status().isOk())
	        		.andReturn();
	        TimeLockedAddressTO response = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
	        
	        TimeLockedAddress timeLockedAddress = response.timeLockedAddress();
	        Address address = timeLockedAddress.getAddress(params);
	        addressesToRedeemScripts.put(address, timeLockedAddress.createRedeemScript().getProgram());
	        mostRecentAddress = address;
	        
	        if (wallet() != null) {
	        	wallet().addWatchedAddress(address);
	        }
	        return timeLockedAddress;
		}
		
		public SignTO signTxByServer(Transaction tx) throws Exception {
			SignTO signTO = new SignTO()
					.clientPublicKey(clientKey.getPubKey())
					.transaction(tx.unsafeBitcoinSerialize())
					.setSuccess();
			
			MvcResult res = mockMvc
	        		.perform(post("/v3/payment/signtx").secure(true)
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

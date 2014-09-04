package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.keys.CustomKeyPair;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerPayOutRule;
import ch.uzh.csg.mbps.server.dao.ServerPayOutRuleDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.service.ServerPayOutRuleService;
import ch.uzh.csg.mbps.server.service.ServerPayOutTransactionService;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.test.ReplacementDataSetLoader;
import ch.uzh.csg.mbps.server.util.web.ServerPayOutRulesTransferObject;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
@DbUnitConfiguration(databaseConnection="dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerPayOutRuleServiceTest {

	@Autowired
	private ServerPayOutRuleDAO serverPayOutRuleDAO;
	@Autowired
	private IServerPayOutRule serverPayOutRuleService;
	@Autowired
	private IServerAccount serverAccountService;
	
	private static boolean initialized = false;
	@Rule
	public ExpectedException exception = ExpectedException.none();
	
	@Before
	public void setUp() throws Exception {
		ServerPayOutRuleService.testingMode = true;
		ServerPayOutTransactionService.testingMode = true;
		ServerAccountService.enableTestingMode();
		if (!initialized){		
			KeyPair keypair = KeyHandler.generateKeyPair();
			
			Constants.SERVER_KEY_PAIR = new CustomKeyPair(PKIAlgorithm.DEFAULT.getCode(), (byte) 1, KeyHandler.encodePublicKey(keypair.getPublic()), KeyHandler.encodePrivateKey(keypair.getPrivate()));
			initialized = true;
		}
	}
	
	@After
	public void teardown(){
		ServerAccountService.disableTestingMode();
		ServerPayOutRuleService.testingMode = true;
		ServerPayOutTransactionService.testingMode = true;
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutRuleData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverPayOutRuleExpectedCreateRuleData.xml", table="server_payout_rules")
	public void testCreateRule() throws ServerAccountNotFoundException, BitcoinException, ServerPayOutRulesAlreadyDefinedException, ServerPayOutRuleNotFoundException{
		ServerAccount account = serverAccountService.getById(15);
		
		ServerPayOutRule first = new ServerPayOutRule(account.getId(), 15, 2, account.getPayoutAddress());
		ServerPayOutRule second = new ServerPayOutRule(account.getId(), 15, 4, account.getPayoutAddress());
		ServerPayOutRule third = new ServerPayOutRule(account.getId(), 15, 6, account.getPayoutAddress());
		
		List<ServerPayOutRule> rules = new ArrayList<ServerPayOutRule>();
		rules.add(first);
		rules.add(second);
		rules.add(third);
		
		ServerPayOutRulesTransferObject spot = new ServerPayOutRulesTransferObject();
		spot.setPayOutRulesList(rules);
		
		serverPayOutRuleService.createRule(spot, account.getUrl());
		
		List<ServerPayOutRule> accountRules = serverPayOutRuleService.getRulesByUrl(account.getUrl());
		
		assertTrue(accountRules.size() == 3);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutRuleData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void checkBalanceLimitRules() throws Exception{
		ServerAccount fromDB = serverAccountService.getByUrl("https://www.haus.ch");
		assertTrue(fromDB.getActiveBalance().abs().compareTo(new BigDecimal("0.52"))==0);
		serverPayOutRuleService.checkBalanceLimitRules(fromDB);

		fromDB = serverAccountService.getByUrl("https://www.haus.ch");
		assertTrue(fromDB.getActiveBalance().abs().compareTo(BigDecimal.ZERO)==0);
		serverPayOutRuleService.checkBalanceLimitRules(fromDB);
	}

	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutRuleData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void checkAllRules() throws Exception{
		ServerAccount fromDB = serverAccountService.getByUrl("https://www.my_url.ch");
		assertEquals(serverPayOutRuleService.getRulesById(fromDB.getId()).size(), 3);
		assertTrue(fromDB.getActiveBalance().abs().compareTo(BigDecimal.ONE)==0);

		//match the day and hour in the coressponding db unit file
		serverPayOutRuleService.checkAllRules();
		
		fromDB = serverAccountService.getByUrl("https://www.my_url.ch");
		assertTrue(fromDB.getActiveBalance().abs().compareTo(BigDecimal.ZERO)==0);
		serverPayOutRuleService.checkAllRules();
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutRuleData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void checkGetRules() throws Exception {
		ServerAccount fromDB = serverAccountService.getByUrl("http://test.com");

		List<ServerPayOutRule> resultList = serverPayOutRuleService.getRulesByUrl(fromDB.getUrl());
		assertEquals(resultList.size(), 2);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutRuleData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverPayOutRuleExpectedDeletedData.xml", table="server_payout_rules")
	public void checkDeleteRules() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, PayOutRulesAlreadyDefinedException, PayOutRuleNotFoundException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException, ServerAccountNotFoundException, ServerPayOutRuleNotFoundException {
		ServerAccount fromDB = serverAccountService.getByUrl("http://www.fake_address.org");
		
		List<ServerPayOutRule> resultList = serverPayOutRuleService.getRulesByUrl(fromDB.getUrl());
		assertEquals(resultList.size(), 2);
		
		serverPayOutRuleService.deleteRules(fromDB.getUrl());
		
		exception.expect(ServerPayOutRuleNotFoundException.class);
		serverPayOutRuleService.getRulesByUrl(fromDB.getUrl());
	}
}

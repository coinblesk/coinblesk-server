package ch.uzh.csg.coinblesk.server.service;

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
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.keys.CustomKeyPair;
import ch.uzh.csg.coinblesk.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutRule;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.domain.PayOutRule;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.security.KeyHandler;
import ch.uzh.csg.coinblesk.server.service.PayOutRuleService;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import ch.uzh.csg.coinblesk.server.util.BitcoindController;
import ch.uzh.csg.coinblesk.server.util.Constants;
import ch.uzh.csg.coinblesk.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})

public class PayOutRuleServiceTest {
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Autowired
	private IPayOutRule payOutRuleService;
	
	private static boolean initialized = false;
	private static UserAccount test51;
	private static UserAccount test52;
	private static UserAccount test53;
	private static UserAccount test54;
	
	 @Rule
	 public ExpectedException exception = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		BitcoindController.TESTING = true;
		UserAccountService.enableTestingMode();
		
		if (!initialized) {
			test51 = new UserAccount("test51@https://mbps.csg.uzh.ch", "chuck51@bitcoin.csg.uzh.ch", "asdf");
			test52 = new UserAccount("test52@https://mbps.csg.uzh.ch", "chuck52@bitcoin.csg.uzh.ch", "asdf");
			test53 = new UserAccount("test53@https://mbps.csg.uzh.ch", "chuck53@bitcoin.csg.uzh.ch", "asdf");
			test54 = new UserAccount("test54@https://mbps.csg.uzh.ch", "chuck54@bitcoin.csg.uzh.ch", "asdf");

			KeyPair keypair = KeyHandler.generateKeyPair();

			Constants.SERVER_KEY_PAIR = new CustomKeyPair(PKIAlgorithm.DEFAULT.getCode(), (byte) 1, KeyHandler.encodePublicKey(keypair.getPublic()), KeyHandler.encodePrivateKey(keypair.getPrivate()));

			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
	}

	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		assertTrue(userAccountService.createAccount(userAccount));
		userAccount = userAccountService.getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		userAccountService.updateAccount(userAccount);
	}

	@Test
	public void checkBalanceLimitRules() throws Exception{
		createAccountAndVerifyAndReload(test51,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = userAccountService.getByUsername(test51.getUsername());

		ch.uzh.csg.coinblesk.model.PayOutRule por = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por.setBalanceLimitBTC(BigDecimal.ONE);
		por.setUserId(fromDB.getId());
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");

		ch.uzh.csg.coinblesk.model.PayOutRule por2 = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> list = new ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule>();
		list.add(por);
		list.add(por2);
		porto.setPayOutRulesList(list);

		payOutRuleService.createRule(porto, test51.getUsername());

		fromDB = userAccountService.getByUsername(test51.getUsername());

		assertTrue(fromDB.getBalance().compareTo(new BigDecimal("2"))==0);

		payOutRuleService.checkBalanceLimitRules(fromDB);

		fromDB = userAccountService.getByUsername(test51.getUsername());

		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ZERO)==0);

		payOutRuleService.checkBalanceLimitRules(fromDB);

	}

	@Test
	public void checkAllRules() throws Exception{
		PayOutRuleService.testingMode = true;
		createAccountAndVerifyAndReload(test52,BigDecimal.ONE);

		UserAccount fromDB = userAccountService.getByUsername(test52.getUsername());
		ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> list = new ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule>();

		for(int i=1; i<8;i++){
			for(int j=0; j<24; j++){
				ch.uzh.csg.coinblesk.model.PayOutRule por2 = new ch.uzh.csg.coinblesk.model.PayOutRule();
				por2.setDay(i);
				por2.setHour(j);
				por2.setUserId(fromDB.getId());
				por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");
				list.add(por2);
			}			
		}

		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		porto.setPayOutRulesList(list);

		payOutRuleService.createRule(porto, test52.getUsername());

		assertEquals(payOutRuleService.getRules(fromDB.getId()).size(), list.size());


		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ONE)==0);

		payOutRuleService.checkAllRules();

		fromDB = userAccountService.getByUsername(test52.getUsername());

		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ZERO)==0);

		payOutRuleService.checkAllRules();

		PayOutRuleService.testingMode = false;
	}
	
	@Test
	public void checkGetRules() throws Exception {
		createAccountAndVerifyAndReload(test53,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = userAccountService.getByUsername(test53.getUsername());

		ch.uzh.csg.coinblesk.model.PayOutRule por1 = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por1.setDay(5);
		por1.setHour(13);
		por1.setUserId(fromDB.getId());
		por1.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		ch.uzh.csg.coinblesk.model.PayOutRule por2 = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> list = new ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule>();
		list.add(por1);
		list.add(por2);
		porto.setPayOutRulesList(list);

		payOutRuleService.createRule(porto, test53.getUsername());
		
		List<PayOutRule> resultList = payOutRuleService.getRules(fromDB.getUsername());
		assertEquals(resultList.size(), list.size());
	}
	
	@Test
	public void checkDeleteRules() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, PayOutRulesAlreadyDefinedException, PayOutRuleNotFoundException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		createAccountAndVerifyAndReload(test54,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = userAccountService.getByUsername(test54.getUsername());

		ch.uzh.csg.coinblesk.model.PayOutRule por1 = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por1.setDay(5);
		por1.setHour(13);
		por1.setUserId(fromDB.getId());
		por1.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		ch.uzh.csg.coinblesk.model.PayOutRule por2 = new ch.uzh.csg.coinblesk.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> list = new ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule>();
		list.add(por1);
		list.add(por2);
		porto.setPayOutRulesList(list);

		payOutRuleService.createRule(porto, test54.getUsername());
		
		List<PayOutRule> resultList = payOutRuleService.getRules(fromDB.getUsername());
		assertEquals(resultList.size(), list.size());
		
		payOutRuleService.deleteRules(fromDB.getUsername());
		
		
		exception.expect(PayOutRuleNotFoundException.class);
		payOutRuleService.getRules(fromDB.getUsername());


	}
}

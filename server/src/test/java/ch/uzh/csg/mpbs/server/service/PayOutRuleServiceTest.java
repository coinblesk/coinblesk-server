package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.mbps.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.PayOutRuleService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.util.KeyHandler;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"file:src/main/webapp/WEB-INF/applicationContext.xml",
		"file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml",
"file:src/main/webapp/WEB-INF/spring-security.xml" })
@WebAppConfiguration
public class PayOutRuleServiceTest {
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private FilterChainProxy springSecurityFilterChain;
	private static boolean initialized = false;
	private static UserAccount test51;
	private static UserAccount test52;
	private static UserAccount test53;
	private static UserAccount test54;
	
	 @Rule
	 public ExpectedException exception = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		
		if (!initialized) {

			test51 = new UserAccount("test51", "chuck51@bitcoin.csg.uzh.ch", "asdf");
			test52 = new UserAccount("test52", "chuck52@bitcoin.csg.uzh.ch", "asdf");
			test53 = new UserAccount("test53", "chuck53@bitcoin.csg.uzh.ch", "asdf");
			test54 = new UserAccount("test54", "chuck54@bitcoin.csg.uzh.ch", "asdf");

			KeyPair keypair = KeyHandler.generateKeys();

			Constants.PRIVATEKEY = keypair.getPrivate();
			Constants.PUBLICKEY = keypair.getPublic();

			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
	}

	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		assertTrue(UserAccountService.getInstance().createAccount(userAccount));
		userAccount = UserAccountService.getInstance().getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		UserAccountDAO.updateAccount(userAccount);
	}

	@Test
	public void checkBalanceLimitRules() throws Exception{
		createAccountAndVerifyAndReload(test51,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test51.getUsername());

		ch.uzh.csg.mbps.model.PayOutRule por = new ch.uzh.csg.mbps.model.PayOutRule();
		por.setBalanceLimit(BigDecimal.ONE);
		por.setUserId(fromDB.getId());
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");

		ch.uzh.csg.mbps.model.PayOutRule por2 = new ch.uzh.csg.mbps.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		list.add(por);
		list.add(por2);
		porto.setPayOutRulesList(list);

		PayOutRuleService.getInstance().createRule(porto, test51.getUsername());

		fromDB = UserAccountService.getInstance().getByUsername(test51.getUsername());

		assertTrue(fromDB.getBalance().compareTo(new BigDecimal("2"))==0);

		PayOutRuleService.getInstance().checkBalanceLimitRules(fromDB);

		fromDB = UserAccountService.getInstance().getByUsername(test51.getUsername());

		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ZERO)==0);

		PayOutRuleService.getInstance().checkBalanceLimitRules(fromDB);

	}

	@Test
	public void checkAllRules() throws Exception{
		PayOutRuleService.testingMode = true;
		createAccountAndVerifyAndReload(test52,BigDecimal.ONE);

		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test52.getUsername());
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();

		for(int i=1; i<8;i++){
			for(int j=0; j<24; j++){
				ch.uzh.csg.mbps.model.PayOutRule por2 = new ch.uzh.csg.mbps.model.PayOutRule();
				por2.setDay(i);
				por2.setHour(j);
				por2.setUserId(fromDB.getId());
				por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");
				list.add(por2);
			}			
		}

		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		porto.setPayOutRulesList(list);

		PayOutRuleService.getInstance().createRule(porto, test52.getUsername());

		assertEquals(PayOutRuleService.getInstance().getRules(fromDB.getId()).size(), list.size());


		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ONE)==0);

		PayOutRuleService.getInstance().checkAllRules();

		fromDB = UserAccountService.getInstance().getByUsername(test52.getUsername());

		assertTrue(fromDB.getBalance().compareTo(BigDecimal.ZERO)==0);

		PayOutRuleService.getInstance().checkAllRules();

		PayOutRuleService.testingMode = false;
	}
	
	@Test
	public void checkGetRules() throws Exception {
		createAccountAndVerifyAndReload(test53,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test53.getUsername());

		ch.uzh.csg.mbps.model.PayOutRule por1 = new ch.uzh.csg.mbps.model.PayOutRule();
		por1.setDay(5);
		por1.setHour(13);
		por1.setUserId(fromDB.getId());
		por1.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		ch.uzh.csg.mbps.model.PayOutRule por2 = new ch.uzh.csg.mbps.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		list.add(por1);
		list.add(por2);
		porto.setPayOutRulesList(list);

		PayOutRuleService.getInstance().createRule(porto, test53.getUsername());
		
		ArrayList<PayOutRule> resultList = PayOutRuleService.getInstance().getRules(fromDB.getUsername());
		assertEquals(resultList.size(), list.size());
	}
	
	@Test
	public void checkDeleteRules() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, PayOutRulesAlreadyDefinedException, PayOutRuleNotFoundException, InvalidEmailException, EmailAlreadyExistsException {
		createAccountAndVerifyAndReload(test54,BigDecimal.ONE.add(BigDecimal.ONE));
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test54.getUsername());

		ch.uzh.csg.mbps.model.PayOutRule por1 = new ch.uzh.csg.mbps.model.PayOutRule();
		por1.setDay(5);
		por1.setHour(13);
		por1.setUserId(fromDB.getId());
		por1.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		ch.uzh.csg.mbps.model.PayOutRule por2 = new ch.uzh.csg.mbps.model.PayOutRule();
		por2.setDay(5);
		por2.setHour(13);
		por2.setUserId(fromDB.getId());
		por2.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");


		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		list.add(por1);
		list.add(por2);
		porto.setPayOutRulesList(list);

		PayOutRuleService.getInstance().createRule(porto, test54.getUsername());
		
		ArrayList<PayOutRule> resultList = PayOutRuleService.getInstance().getRules(fromDB.getUsername());
		assertEquals(resultList.size(), list.size());
		
		PayOutRuleService.getInstance().deleteRules(fromDB.getUsername());
		
		
		exception.expect(PayOutRuleNotFoundException.class);
		PayOutRuleService.getInstance().getRules(fromDB.getUsername());


	}
}

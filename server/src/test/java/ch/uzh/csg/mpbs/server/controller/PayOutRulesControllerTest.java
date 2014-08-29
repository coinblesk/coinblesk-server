package ch.uzh.csg.mpbs.server.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.keys.CustomKeyPair;
import ch.uzh.csg.mbps.model.PayOutRule;
import ch.uzh.csg.mbps.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.controller.PayOutRulesController;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.json.CustomObjectMapper;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml",
		"classpath:view.xml",
		"classpath:security.xml"})
@WebAppConfiguration
public class PayOutRulesControllerTest {

	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private FilterChainProxy springSecurityFilterChain;
	
	@Autowired
	private IUserAccount userAccountService;
	
	private static MockMvc mockMvc;
	private static boolean initialized = false;
	private static UserAccount test41;
	private static UserAccount test42;
	private static UserAccount test43;

	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();

			test41 = new UserAccount("test41@https://mbps.csg.uzh.ch", "test41@bitcoin.csg.uzh.ch", "asdf");
			test42 = new UserAccount("test42@https://mbps.csg.uzh.ch", "test42@bitcoin.csg.uzh.ch", "asdf");
			test43 = new UserAccount("test43@https://mbps.csg.uzh.ch", "test43@bitcoin.csg.uzh.ch", "asdf");

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
	public void testGetPayOutRuleNoneDefined() throws Exception {

		createAccountAndVerifyAndReload(test41,BigDecimal.ONE);
		String plainTextPw = test41.getPassword();

		HttpSession session = loginAndGetSession(test41.getUsername(), plainTextPw);
		
		CustomObjectMapper mapper = new CustomObjectMapper();

		MvcResult mvcResult = mockMvc.perform(get("/rules/get").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		PayOutRulesTransferObject por = mapper.readValue(mvcResult.getResponse().getContentAsString(), PayOutRulesTransferObject.class);
		assertFalse(por.isSuccessful());

		List<PayOutRule> por2 = por.getPayOutRulesList();

		assertNull(por2);

	}

	@Test
	public void testCreateAndGetPayOutRule() throws Exception {
		
		BitcoindController.TESTING = true;

		createAccountAndVerifyAndReload(test42,BigDecimal.ONE);
		String plainTextPw = test42.getPassword();

		ch.uzh.csg.mbps.model.PayOutRule por = new ch.uzh.csg.mbps.model.PayOutRule();
		por.setDay(2);
		por.setHour(17);
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");

		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		list.add(por);
		porto.setPayOutRulesList(list);

		HttpSession session = loginAndGetSession(test42.getUsername(), plainTextPw);


		CustomObjectMapper mapper = new CustomObjectMapper();
		String asString = mapper.writeValueAsString(porto);

		MvcResult mvcResult = mockMvc.perform(post("/rules/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();	

		mvcResult = mockMvc.perform(get("/rules/get").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		PayOutRulesTransferObject porto2 = mapper.readValue(mvcResult.getResponse().getContentAsString(), PayOutRulesTransferObject.class);
		assertTrue(porto2.isSuccessful());


		assertNotNull(porto2.getPayOutRulesList().get(0));
		String s1 = porto2.getPayOutRulesList().get(0).toString();
		String s2 = por.toString();
		assertEquals(s1, s2);

		logout(mvcResult);
	}

	@Test
	public void testResetPayOutRules() throws Exception {
		
		BitcoindController.TESTING = true;

		createAccountAndVerifyAndReload(test43,BigDecimal.ONE);
		String plainTextPw = test43.getPassword();

		ch.uzh.csg.mbps.model.PayOutRule por = new ch.uzh.csg.mbps.model.PayOutRule();
		por.setDay(2);
		por.setHour(17);
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");

		PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		list.add(por);
		porto.setPayOutRulesList(list);

		HttpSession session = loginAndGetSession(test43.getUsername(), plainTextPw);


		CustomObjectMapper mapper = new CustomObjectMapper();
		String asString = mapper.writeValueAsString(porto);

		MvcResult mvcResult = mockMvc.perform(post("/rules/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();	

		mvcResult = mockMvc.perform(get("/rules/get").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		String result = mvcResult.getResponse().getContentAsString();
		PayOutRulesTransferObject porto2 = mapper.readValue(result, PayOutRulesTransferObject.class);
		assertTrue(porto2.isSuccessful());

		assertNotNull(porto2.getPayOutRulesList().get(0));

		assertEquals(porto2.getPayOutRulesList().get(0).toString(),por.toString());

		mvcResult = mockMvc.perform(get("/rules/reset").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		MvcResult mvcResult3 = mockMvc.perform(get("/rules/get").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		PayOutRulesTransferObject porto3 = mapper.readValue(mvcResult3.getResponse().getContentAsString(), PayOutRulesTransferObject.class);
		assertFalse(porto3.isSuccessful());

		assertNull(porto3.getPayOutRulesList());


		logout(mvcResult);
	}

	@Test
	public void testTransform()  {

		ch.uzh.csg.mbps.server.domain.PayOutRule por = new ch.uzh.csg.mbps.server.domain.PayOutRule();
		por.setBalanceLimit(BigDecimal.ONE);
		por.setDay(3);
		por.setHour(12);
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");
		por.setUserId(1);
		ch.uzh.csg.mbps.server.domain.PayOutRule por2 = new ch.uzh.csg.mbps.server.domain.PayOutRule();
		por.setBalanceLimit(BigDecimal.ONE);
		por.setDay(4);
		por.setHour(13);
		por.setPayoutAddress("msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW");
		por.setUserId(2);
		List<ch.uzh.csg.mbps.server.domain.PayOutRule> list = new ArrayList<ch.uzh.csg.mbps.server.domain.PayOutRule>();
		list.add(por);
		list.add(por2);
		PayOutRulesController controller = new PayOutRulesController();
		List<ch.uzh.csg.mbps.model.PayOutRule> list2 = controller.transform(list);
		ch.uzh.csg.mbps.model.PayOutRule por3 = list2.get(0);
		ch.uzh.csg.mbps.model.PayOutRule por4 = list2.get(1);
		assertEquals(por3.getBalanceLimitBTC(), por.getBalanceLimit());
		assertEquals(por3.getDay().intValue(), por.getDay());
		assertEquals(por3.getHour().intValue(), por.getHour());
		assertEquals(por3.getPayoutAddress(), por.getPayoutAddress());

		assertEquals(por4.getBalanceLimitBTC(), por2.getBalanceLimit());
		assertEquals(por4.getDay().intValue(), por2.getDay());
		assertEquals(por4.getHour().intValue(), por2.getHour());
		assertEquals(por4.getPayoutAddress(), por2.getPayoutAddress());
	}

	private void logout(MvcResult result) {
		result.getRequest().getSession().invalidate();
	}

	private HttpSession loginAndGetSession(String username, String plainTextPassword) throws Exception {
		HttpSession session = mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", username).param("j_password", plainTextPassword))
				.andExpect(status().isOk())
				.andReturn()
				.getRequest()
				.getSession();

		return session;
	}
}

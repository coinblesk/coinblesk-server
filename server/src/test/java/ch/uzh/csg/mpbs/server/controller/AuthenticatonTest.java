package ch.uzh.csg.mpbs.server.controller;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import javax.servlet.http.HttpSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"file:src/main/webapp/WEB-INF/applicationContext.xml",
		"file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml",
		"file:src/main/webapp/WEB-INF/spring-security.xml"})
@WebAppConfiguration
public class AuthenticatonTest {
	
	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private static MockMvc mockMvc;
	
	private static boolean initialized = false;
	private static UserAccount test60;
	private static UserAccount test61;
	private static UserAccount test62;
	private static UserAccount test63;
	
	@Before
	public void setUp() {
		UserAccountService.enableTestingMode();

		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();

			test60 = new UserAccount("test60", "test60@bitcoin.csg.uzh.ch", "asdf");
			test61 = new UserAccount("test61_1", "test61@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test62 = new UserAccount("test62_1", "test62_1@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test63 = new UserAccount("test63", "test63@bitcoin.csg.uzh.ch", "i-don't-need-one");

			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		UserAccountService.disableTestingMode();
	}
	
	@Test
	public void testLoginLogout() throws Exception {
		mockMvc.perform(get("/user/afterLogin").secure(false)).andExpect(status().isUnauthorized());

		String plainTextPassword = test60.getPassword();
		createAccountAndVerifyAndReload(test60, new BigDecimal(0.0));

		HttpSession session = loginAndGetSession(test60.getUsername(), plainTextPassword);

		MvcResult result = mockMvc.perform(get("/user/afterLogin").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();

		logout(result);

		mockMvc.perform(get("/user/afterLogin").secure(false)).andExpect(status().isUnauthorized());
	}
	
	@Test
	public void testLoginEmailNotVerified() throws Exception {	  
		String plainTextPassword = test61.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test61));

		mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", test61.getUsername()).param("j_password", plainTextPassword))
				.andExpect(status().isUnauthorized())
				.andReturn();
	}
	
	@Test
	public void testLoginBadCredentials() throws Exception {
		String plainTextPassword = test61.getPassword();
		createAccountAndVerifyAndReload(test62, new BigDecimal(0.0));
		
		mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", test62.getUsername()).param("j_password", "wrongPassword"))
				.andExpect(status().isUnauthorized())
				.andReturn();
		
		mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", "wrongUsername").param("j_password", plainTextPassword))
				.andExpect(status().isUnauthorized())
				.andReturn();
	}

	@Test
	public void testSessionTimeout() throws Exception {
		mockMvc.perform(get("/user/afterLogin").secure(false)).andExpect(status().isUnauthorized());

		String plainTextPassword = test63.getPassword();
		createAccountAndVerifyAndReload(test63, new BigDecimal(0.0));

		HttpSession session = loginAndGetSession(test63.getUsername(), plainTextPassword);

		mockMvc.perform(get("/user/afterLogin").secure(false).session((MockHttpSession) session)).andExpect(status().isOk());

		session.setMaxInactiveInterval(5);
		Thread.sleep(6);

		mockMvc.perform(get("/user/afterLogin").secure(false)).andExpect(status().isUnauthorized());
	}
	
	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		assertTrue(UserAccountService.getInstance().createAccount(userAccount));
		userAccount = UserAccountService.getInstance().getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		UserAccountDAO.updateAccount(userAccount);
	}

	private HttpSession loginAndGetSession(String username, String plainTextPassword) throws Exception {
		HttpSession session = mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", username).param("j_password", plainTextPassword))
				.andExpect(status().isOk())
				.andReturn()
				.getRequest()
				.getSession();

		return session;
	}
	
	private void logout(MvcResult result) {
		result.getRequest().getSession().invalidate();
	}
	
}

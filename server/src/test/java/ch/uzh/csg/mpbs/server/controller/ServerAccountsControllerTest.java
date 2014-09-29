package ch.uzh.csg.mpbs.server.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpSession;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.mbps.responseobject.ServerAccountTransferObject;
import ch.uzh.csg.mbps.responseobject.ServerAccountsRequestObject;
import ch.uzh.csg.mbps.server.json.CustomObjectMapper;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mpbs.server.utilTest.ReplacementDataSetLoader;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml",
		"classpath:view.xml",
		"classpath:security.xml"})
@DbUnitConfiguration(databaseConnection="dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
@WebAppConfiguration
public class ServerAccountsControllerTest {

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private static MockMvc mockMvc;
	
	private static final String PLAIN_TEXT_PASSWORD = "wwww";
	
	private static boolean initialized = false;
	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		ServerAccountService.enableTestingMode();
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
			
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
		ServerAccountService.disableTestingMode();
	}
	
	private HttpSession loginAndGetSession(String username, String plainTextPassword) throws Exception {
		HttpSession session = mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", username).param("j_password", plainTextPassword))
				.andExpect(status().isOk())
				.andReturn()
				.getRequest()
				.getSession();

		return session;
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Controllers/userAccountServerAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetAccounts() throws Exception{
		HttpSession session = loginAndGetSession("martin@http://server.own.org", PLAIN_TEXT_PASSWORD);
		
		ServerAccountsRequestObject obj = new ServerAccountsRequestObject();
		obj.setUrlPage(0);
		
		
		CustomObjectMapper mapper = new CustomObjectMapper();
		String mappedString = mapper.writeValueAsString(obj);
		
		MvcResult mvcResult = mockMvc.perform(post("/serveraccounts/accounts").secure(false).
				contentType(MediaType.APPLICATION_JSON).content(mappedString))
				.andExpect(status().isUnauthorized())
				.andReturn();
		
		assertTrue(mvcResult.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED);
		
		mvcResult = mockMvc.perform(post("/serveraccounts/accounts").secure(false).session((MockHttpSession)session)
				.contentType(MediaType.APPLICATION_JSON).content(mappedString))
				.andExpect(status().isOk())
				.andReturn();
		
		String contentAsString = mvcResult.getResponse().getContentAsString();
		ServerAccountTransferObject response = mapper.readValue(contentAsString, ServerAccountTransferObject.class);
		
		assertNotNull(response);
		assertTrue(response.isSuccessful());
		assertEquals(6, response.getServerAccountList().size());
		
		
	}
}

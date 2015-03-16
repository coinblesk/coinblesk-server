package ch.uzh.csg.mpbs.server.controllerui;

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

import ch.uzh.csg.coinblesk.server.json.CustomObjectMapper;
import ch.uzh.csg.coinblesk.server.service.ActivitiesService;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import ch.uzh.csg.coinblesk.server.web.response.ActivitiesTransferObject;
import ch.uzh.csg.coinblesk.server.web.response.UserAccountTransferObject;
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
public class UsersControllerTest {
	
	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private static MockMvc mockMvc;
	
	private static boolean initialized = false;
	
	private final String PLAIN_TEXT_PASSWORD = "wwww";
	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
			
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
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
	public void testGetUsers() throws Exception{
		HttpSession session = loginAndGetSession("peter@http://server.own.org", PLAIN_TEXT_PASSWORD);
		
		CustomObjectMapper mapper = new CustomObjectMapper();
		
		
		MvcResult mvcResult = mockMvc.perform(post("/users/all").secure(false).session((MockHttpSession)session)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();
		
		String contentAsString = mvcResult.getResponse().getContentAsString();
		UserAccountTransferObject response = mapper.readValue(mvcResult.getResponse().getContentAsString(), UserAccountTransferObject.class);
		
//		UserAccountTransferObject response = new UserAccountTransferObject();
//		response.decode(contentAsString);
		
		assertNotNull(response);
		assertTrue(response.isSuccessful());
		assertEquals(4, response.getUserAccountObjectList().size());
	}
	

	
}
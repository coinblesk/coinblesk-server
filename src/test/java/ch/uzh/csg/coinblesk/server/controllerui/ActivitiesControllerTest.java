package ch.uzh.csg.coinblesk.server.controllerui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.http.HttpSession;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;
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

import ch.uzh.csg.coinblesk.server.service.ActivitiesService;
import ch.uzh.csg.coinblesk.server.util.Credentials;
import ch.uzh.csg.coinblesk.server.utilTest.ReplacementDataSetLoader;
import ch.uzh.csg.coinblesk.server.web.response.ActivitiesTransferObject;

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
public class ActivitiesControllerTest {
	
	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private static MockMvc mockMvc;
	
	private static boolean initialized = false;
	
	private final String PLAIN_TEXT_PASSWORD = "wwww";
	
    
  @BeforeClass
  public static void setUpClass() throws Exception {
      // mock JNDI
      SimpleNamingContextBuilder contextBuilder = new SimpleNamingContextBuilder();
      Credentials credentials = new Credentials();
      contextBuilder.bind("java:comp/env/bean/CredentialsBean", credentials);
      contextBuilder.activate();
  }
	
	@Before
	public void setUp() throws Exception {
		ActivitiesService.enableTestingMode();
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
			
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		ActivitiesService.disableTestingMode();
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
	@DatabaseSetup(value="classpath:DbUnitFiles/Controllers/activitiesDataController.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetLogs() throws Exception{
		HttpSession session = loginAndGetSession("martin@http://server.own.org", PLAIN_TEXT_PASSWORD);
	
		MvcResult mvcResult = mockMvc.perform(post("/activities/logs").secure(false).session((MockHttpSession)session)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andReturn();
		
		String contentAsString = mvcResult.getResponse().getContentAsString();
		ActivitiesTransferObject response = new ActivitiesTransferObject();
		response.decode(contentAsString);
		
		assertNotNull(response);
		assertTrue(response.isSuccessful());
		assertEquals(10, response.getActivitiessList().size());
	}
	

	
}
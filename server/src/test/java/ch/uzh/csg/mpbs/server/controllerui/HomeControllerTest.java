package ch.uzh.csg.mpbs.server.controllerui;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;

import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.service.UserAccountService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:test-applicationContext.xml",
		"classpath:applicationContext.xml",
		"classpath:mvc-dispatcher-servlet.xml",
		"classpath:spring-security.xml"})
@DbUnitConfiguration(databaseConnection="dataSource")
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
@WebAppConfiguration
public class HomeControllerTest {
	
	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private FilterChainProxy springSecurityFilterChain;

	private static MockMvc mockMvc;

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
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Controllers/homeControllerUserAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Controllers/homeControllerUserAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testGetPageHome_FailedNotAuthorized() throws Exception{
		mockMvc.perform(get("/home").secure(false)).andExpect(status().isUnauthorized());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Controllers/homeControllerUserAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Controllers/homeControllerUserAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testGetBalance(){
		
	}
	
}

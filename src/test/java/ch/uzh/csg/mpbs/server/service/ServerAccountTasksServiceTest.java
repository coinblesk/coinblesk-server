package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.coinblesk.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.coinblesk.server.domain.ServerAccountTasks;
import ch.uzh.csg.coinblesk.server.service.ServerAccountTasksService;
import ch.uzh.csg.coinblesk.server.service.ServerAccountTasksService.ServerAccountTaskTypes;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mpbs.server.utilTest.ReplacementDataSetLoader;

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
public class ServerAccountTasksServiceTest {
	private static boolean initialized = false;

	@Autowired
	private IServerAccountTasks serverAccountTasksService;
	
	@Before
	public void setUp() throws Exception {
		ServerAccountTasksService.enableTestingMode();
		if (!initialized){		
			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		ServerAccountTasksService.disableTestingMode();
	}
	  
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/tasksServerAccountData.xml", type=DatabaseOperation.CLEAN_INSERT)
	public void testPersistsNewCreatedAccount() throws ServerAccountNotFoundException{
		String url = "https://www.newAccount.ch";
		String email = "new@mail.ch";
		String username = "hans@http://server.own.org";
		ServerAccountTasks task = serverAccountTasksService.getAccountTasksByUrl(url, ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());

		assertTrue(task == null);

		List<ServerAccountTasks> before = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(5, before.size());
		
		serverAccountTasksService.persistsCreateNewAccount(url, username, email);
		
		ServerAccountTasks taskNew = serverAccountTasksService.getAccountTasksByUrl(url, ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertNotNull(taskNew);
		
		assertEquals("123456", taskNew.getToken());
		assertEquals(1, taskNew.getId());
		assertEquals(username, taskNew.getUsername());
		assertEquals(url, taskNew.getUrl());
	
		List<ServerAccountTasks> after1 = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(6, after1.size());
		
		url = "http://newPayout.ch";
		email = "new@mail.ch";
		username = "martin@http://server.own.org";
		String payout = "msgc3DFzszXQx6F5nHi8xdcB2EheKYW7xW";
		task = serverAccountTasksService.getAccountTasksByUrl(url, ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		
		serverAccountTasksService.persistsCreateNewAccountPayOutAddress(url, username, email, payout);
		
		ServerAccountTasks taskNew2 = serverAccountTasksService.getAccountTasksByUrl(url, ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertNotNull(taskNew2);

		assertEquals(url, taskNew2.getUrl());
		assertEquals(username, taskNew2.getUsername());
		assertEquals(payout, taskNew2.getPayoutAddress());
		assertEquals(2, taskNew2.getId());
		assertEquals("123457", taskNew2.getToken());
		
		List<ServerAccountTasks> after2 = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(7, after2.size());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/tasksServerAccountData.xml", type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/tasksServerAccountDeleteData.xml", table="server_account")
	public void testDeleteTask(){
		ServerAccountTasks task = serverAccountTasksService.getAccountTasksByToken("45656577");
		assertNotNull(task);
		
		List<ServerAccountTasks> before = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(5, before.size());
		
		if(ServerAccountTasksService.isValidServerAccountTaskType(task.getType()))
			serverAccountTasksService.deleteTask(task.getType(), task.getToken());
		
		ServerAccountTasks task2 = serverAccountTasksService.getAccountTasksByToken("45656577");
		assertTrue(task2 == null);
		
		List<ServerAccountTasks> after = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertFalse(5 == after.size());
		
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/tasksServerAccountData.xml", type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/tasksServerAccountProceedData.xml", table="server_account")
	public void testProccessTask(){
		List<ServerAccountTasks> before = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(5, before.size());
		
		List<ServerAccountTasks> proceedBefore = serverAccountTasksService.getProceedAccounts();
		assertEquals(0, proceedBefore.size());
		
		int noPayout = 0;
		int withPayout= 0; 
		for(ServerAccountTasks account: before){
			if(account.getPayoutAddress() == null){
				noPayout++;
				serverAccountTasksService.updateProceed(account.getToken());
			} else {
				withPayout++;
				serverAccountTasksService.updateProceed(account.getToken());
			}
		}
		assertEquals(3, noPayout);
		assertEquals(2, withPayout);
		
		List<ServerAccountTasks> after = serverAccountTasksService.processNewAccountTask(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		assertEquals(0, after.size());
		
		List<ServerAccountTasks> proceedAfter = serverAccountTasksService.getProceedAccounts();
		assertEquals(5, proceedAfter.size());
	}
	
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/tasksServerAccountProceedData.xml", type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/tasksServerAccountProceedDataExpected.xml", table="server_account")
	public void testRemoveProceedTasks(){
		assertTrue(serverAccountTasksService.removeProceedTasks("45656577"));
	}
	
}
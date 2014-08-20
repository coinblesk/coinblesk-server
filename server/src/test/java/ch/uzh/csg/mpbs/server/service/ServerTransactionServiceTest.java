package ch.uzh.csg.mpbs.server.service;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.service.ServerAccountService;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
@DbUnitConfiguration(databaseConnection="dataSource")
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
@DatabaseTearDown(value="classpath:DbUnitFiles/Services/serverTransactionData.xml", type=DatabaseOperation.DELETE_ALL)
public class ServerTransactionServiceTest {

	private static boolean initialized = false;
	
	@Autowired
	private IServerTransaction serverTransactionService;
	
	@Before
	public void setUp() throws Exception {
		ServerAccountService.enableTestingMode();
		if (!initialized){		
			initialized = true;
		}
	}
	
	@After
	public void teardown(){
		ServerAccountService.disableTestingMode();
	}
	
	@Test
//	@ExpectedDatabase(value="classpath:DbUnitFiles/serverTransactionExpectedCreateData.xml", table="server_transaction")
	public void testCreateTransaction() {
		//TODO: mehmet needed when communicating with android
	}
	
	@Test
	public void testGetHistory() {
		//TODO: mehmet need createAccount
	}
	
	@Test
	public void testGetPayeeHistory() {
		ArrayList<HistoryServerAccountTransaction> payeeList = serverTransactionService.getPayeeHistory(0);
		System.out.println("*************************** " + payeeList.size());
		assertNotNull(payeeList);
		assertThat(payeeList.size(), is(5));
	}
	
	@Test
	public void testGetPayerHistory() {
		ArrayList<HistoryServerAccountTransaction> payerList = serverTransactionService.getPayerHistory(0);
		System.out.println("*************************** " + payerList.size());
		assertNotNull(payerList);
		assertThat(payerList.size(), is(4));
	}
}

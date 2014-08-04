package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;
import java.util.ArrayList;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.service.ServerTransactionService;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:test-applicationContext.xml"})
@DbUnitConfiguration(databaseConnection="dataSource")
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerTransactionServiceTest {

	private static boolean initialized = false;
	
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
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Services/serverTransactionData.xml", type=DatabaseOperation.DELETE_ALL)
//	@ExpectedDatabase(value="classpath:DbUnitFiles/serverTransactionExpectedCreateData.xml", table="server_transaction")
	public void testCreateTransaction() {
		//TODO: mehmet needed when communicating with android
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Services/serverTransactionData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testGetHistory() {
		//TODO: mehmet need createAccount
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Services/serverTransactionData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testGetPayeeHistory() {
		ArrayList<HistoryServerAccountTransaction> payeeList = ServerTransactionService.getInstance().getPayeeHistory(0);
		
		assertNotNull(payeeList);
		assertEquals(5, payeeList.size());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/Services/serverTransactionData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testGetPayerHistory() {
		ArrayList<HistoryServerAccountTransaction> payerList = ServerTransactionService.getInstance().getPayerHistory(0);
		
		assertNotNull(payerList);
		assertEquals(4, payerList.size());
	}
}

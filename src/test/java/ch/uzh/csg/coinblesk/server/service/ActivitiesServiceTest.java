package ch.uzh.csg.coinblesk.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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

import ch.uzh.csg.coinblesk.server.clientinterface.IActivities;
import ch.uzh.csg.coinblesk.server.domain.Activities;
import ch.uzh.csg.coinblesk.server.util.Subjects;
import ch.uzh.csg.coinblesk.server.utilTest.ReplacementDataSetLoader;

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
public class ActivitiesServiceTest {
	private static boolean initialized = false;

	@Autowired
	private IActivities activitiesService;
	
	@Before
	public void setUp() throws Exception {
		ActivitiesService.enableTestingMode();
		if (!initialized){		
			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		ActivitiesService.disableTestingMode();
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/activitiesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetAllLogs(){
		List<Activities> allLogs = activitiesService.getLogs(0);
		assertNotNull(allLogs);
		
		int nofAllLogs = allLogs.size();

		assertEquals(nofAllLogs,10);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/activitiesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/activitiesExpectedData.xml",table="activities")
	public void testCreatedLog(){
		String username = "martin@http://server.own.org";
		String message = "new entry";
		String subject = Subjects.ACCEPT_UPGRADE_TRUST_LEVEL;
		
		List<Activities> allLogs = activitiesService.getLogs(0);
		assertNotNull(allLogs);
		
		int nofAllLogs = allLogs.size();
		assertEquals(nofAllLogs,10);
		
		activitiesService.activityLog(username, subject, message);
		
		List<Activities> updatedLogs = activitiesService.getLogs(0);
		assertNotNull(updatedLogs);
		
		int nofUpdatedAllLogs = updatedLogs.size();
		assertEquals(nofUpdatedAllLogs,11);
		assertFalse(nofAllLogs==nofUpdatedAllLogs);
	}
}
